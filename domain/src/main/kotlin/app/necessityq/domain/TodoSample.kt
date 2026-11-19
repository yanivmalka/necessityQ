package app.necessityq.domain

/**
 * Real shopping history exported from Microsoft To Do (list "קניות", Mar 2025 – Nov 2026). It documents
 * most trips but not all of them, and use stopped in Sep 2026; [TodoCoverage] accounts for the gaps.
 * Open tasks become list lines; completed tasks become purchases for the statistics.
 * Titles are raw and are run through the same quick-entry parser and brand-agnostic catalogue
 * the app uses for typed entries, so "2 קוטג 9%" and "קוטג'" land on the same item.
 */
object TodoSample {
    data class Row(val isOpen: Boolean, val atMillis: Long, val title: String)

    val rows: List<Row> by lazy {
        val stream = TodoSample::class.java.getResourceAsStream("/todo_history.tsv")
            ?: return@lazy emptyList()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val at = parts[1].toLongOrNull() ?: return@mapNotNull null
                Row(isOpen = parts[0] == "L", atMillis = at, title = parts[2])
            }.toList()
        }
    }

    val openLines: List<Row> get() = rows.filter { it.isOpen }
    val purchases: List<Row> get() = rows.filter { !it.isOpen }
}
