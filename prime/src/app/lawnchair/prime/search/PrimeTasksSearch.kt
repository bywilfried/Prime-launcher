package app.lawnchair.prime.search

import android.content.Context
import android.net.Uri
import java.text.Normalizer
import java.util.Locale

/** Read-only Tasks.org v0 provider integration. Supports local and synced lists. */
object PrimeTasksSearch {
    private val base = Uri.parse("content://org.tasks.api/v0")
    data class Task(val title: String, val detail: String, val uri: Uri)
    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun available(context: Context): Boolean =
        context.packageManager.resolveContentProvider("org.tasks.api", 0) != null

    fun lists(context: Context): List<PrimeSourceSelection.Source> {
        if (!PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.REMINDERS) || !available(context)) return emptyList()
        val result = mutableListOf<PrimeSourceSelection.Source>()
        runCatching {
            context.contentResolver.query(base.buildUpon().appendPath("lists").build(), arrayOf("_id", "title"), null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow("_id")
                val title = c.getColumnIndexOrThrow("title")
                while (c.moveToNext()) result.add(PrimeSourceSelection.Source(c.getLong(id).toString(), c.getString(title).orEmpty()))
            }
        }
        return result
    }

    fun search(context: Context, query: String): List<Task> =
        search(context, query, PrimeSearchResultPreferences.Surface.PRIME_SEARCH)

    fun search(context: Context, query: String, surface: PrimeSearchResultPreferences.Surface): List<Task> {
        if (query.isBlank() || !PrimeSearchPermissions.granted(context, PrimeSearchResultPreferences.Category.REMINDERS) || !available(context)) return emptyList()
        val result = mutableListOf<Task>()
        val includeCompleted = PrimeSearchResultPreferences.showCompletedTasks(context, surface)
        val selected = PrimeSourceSelection.selected(context, "tasks", surface)
        if (selected != null && selected.isEmpty()) return emptyList()
        runCatching {
            // Read a bounded set and match locally: the provider search parameter
            // may reject unaccented text when a task title contains accents.
            val uri = base.buildUpon().appendPath("tasks")
                .appendQueryParameter("limit", "1000")
                .build()
            context.contentResolver.query(uri, arrayOf("_id", "title", "notes", "due_date", "list_id", "completed_at"), null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow("_id")
                val title = c.getColumnIndexOrThrow("title")
                val notes = c.getColumnIndexOrThrow("notes")
                val due = c.getColumnIndexOrThrow("due_date")
                val list = c.getColumnIndexOrThrow("list_id")
                val completed = c.getColumnIndexOrThrow("completed_at")
                val words = fold(query.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
                var scanned = 0
                while (c.moveToNext() && result.size < 30 && scanned++ < 1000) {
                    val taskTitle = c.getString(title).orEmpty()
                    val taskNotes = c.getString(notes).orEmpty()
                    if (!words.all { it in fold(taskTitle + " " + taskNotes) }) continue
                    if (selected != null && c.getLong(list).toString() !in selected) continue
                    if (!includeCompleted && c.getLong(completed) != 0L) continue
                    val dueDate = c.getLong(due)
                    val detail = listOf(
                        if (c.getLong(completed) != 0L) "Terminée" else "",
                        if (dueDate > 0L) java.text.DateFormat.getDateInstance().format(java.util.Date(dueDate)) else "",
                        c.getString(notes).orEmpty().take(100)
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    result.add(Task(c.getString(title).orEmpty(), detail,
                        base.buildUpon().appendPath("tasks").appendPath(c.getLong(id).toString()).build()))
                }
            }
        }
        return result
    }
}
