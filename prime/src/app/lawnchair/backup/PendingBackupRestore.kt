package app.lawnchair.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import java.io.File

/** Apply staged files only before their owning singleton opens them in a new process. */
internal object PendingBackupRestore {
    private fun pending(context: Context, name: String) =
        File(context.filesDir, "pending-backup-restore/$name")

    private fun copyAtomic(source: File, target: File) {
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            source.inputStream().use { it.copyTo(output) }
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun stageRoom(context: Context, source: File) {
        SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use {
                require(it.moveToFirst() && it.getString(0) == "ok") { "Invalid preferences database" }
            }
            val directory = File(context.filesDir, "wallpapers")
            db.beginTransaction()
            try {
                db.rawQuery("SELECT id, imagePath FROM Wallpapers", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val filename = File(cursor.getString(1)).name
                        val target = File(directory, filename)
                        if (filename.isNotBlank() && target.canonicalFile.parentFile == directory.canonicalFile && target.isFile) {
                            db.execSQL("UPDATE Wallpapers SET imagePath = ? WHERE id = ?", arrayOf(target.absolutePath, cursor.getLong(0)))
                        } else {
                            db.execSQL("DELETE FROM Wallpapers WHERE id = ?", arrayOf(cursor.getLong(0)))
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        copyAtomic(source, pending(context, "preferences.db"))
    }

    fun stageDataStore(context: Context, source: File) {
        copyAtomic(source, pending(context, "preferences.preferences_pb"))
    }

    @Synchronized
    fun applyRoom(context: Context) {
        val source = pending(context, "preferences.db")
        if (!source.isFile) return
        val target = context.getDatabasePath("preferences")
        // No Room connection has been opened by this singleton yet.
        copyAtomic(source, target)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = File(target.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) { "Unable to remove stale database journal" }
        }
        check(source.delete()) { "Unable to clear pending Room restore" }
    }

    @Synchronized
    fun applyDataStore(context: Context) {
        val source = pending(context, "preferences.preferences_pb")
        if (!source.isFile) return
        copyAtomic(source, File(context.filesDir, "datastore/preferences.preferences_pb"))
        check(source.delete()) { "Unable to clear pending DataStore restore" }
    }
}
