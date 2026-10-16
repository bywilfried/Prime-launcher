package app.lawnchair.bugreport

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.launcher3.PrimeWidgetTrace
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Exports logcat lines available to the launcher process without requiring ADB.
 * Android may restrict access to logs from other applications.
 */
object PrimeWidgetLogExporter {
    fun export(context: Context) {
        val appContext = context.applicationContext
        Toast.makeText(context, "Préparation des logs…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val directory = File(appContext.cacheDir, "logs").apply { mkdirs() }
                val file = File(directory, "prime-widgets-${System.currentTimeMillis()}.txt")
                val process = ProcessBuilder(
                    "logcat", "-d", "-v", "threadtime", "-t", "5000",
                    "--pid=${Process.myPid()}",
                ).redirectErrorStream(true).redirectOutput(file).start()
                val finished = process.waitFor(12, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    throw IllegalStateException("La collecte des logs a dépassé le délai")
                }
                if (process.exitValue() != 0) {
                    throw IllegalStateException("logcat a échoué (code ${process.exitValue()})")
                }
                file.appendText(PrimeWidgetTrace.exportText(appContext))
                file.appendText(
                    "\n--- Prime Launcher widget diagnostics ---\n" +
                        "Captured: ${java.util.Date()}\n" +
                        "PID: ${Process.myPid()}\n" +
                        "Note: Android limits logcat access to this application's process.\n",
                )
                Handler(Looper.getMainLooper()).post {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("Prime widget logs", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Partager les logs Prime"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Partage impossible : ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Collecte impossible : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
