package app.necessityq.domain

/**
 * "תזכיר לי אחר כך" from a barcode scan: the product, the price seen when scanning, and the branch it was scanned
 * in. It comes back as a "לבדוק שוב" card while preparing the list, and when shopping at that branch again.
 * Stored as one tab-separated line: code, name, price, branch, time, and the generic item to add to the list.
 */
data class ScanReminder(
    val code: String,
    val name: String,
    val price: Double?,
    val branchId: String?,
    val atMillis: Long,
    /** The generic name to put on the list ("חלב 3%"); the product's own name when there is none. */
    val itemName: String? = null,
) {
    fun encode(): String = listOf(code, clean(name), price?.toString().orEmpty(), branchId.orEmpty(), atMillis.toString(), clean(itemName.orEmpty()))
        .joinToString("\t")

    /** What "לרשימה" adds. */
    val listName: String get() = itemName?.takeIf { it.isNotBlank() } ?: name

    companion object {
        private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ')

        /** Null for a line that is not a reminder. Older lines have no generic name. */
        fun decode(line: String): ScanReminder? {
            val f = line.split('\t')
            if (f.size < 5 || f[0].isBlank()) return null
            return ScanReminder(
                code = f[0],
                name = f[1].ifBlank { f[0] },
                price = f[2].toDoubleOrNull(),
                branchId = f[3].ifBlank { null },
                atMillis = f[4].toLongOrNull() ?: 0L,
                itemName = f.getOrNull(5)?.ifBlank { null },
            )
        }

        /** The reminders to show: while preparing, all of them; while shopping, those scanned at [branchId]. */
        fun shown(all: List<ScanReminder>, shopping: Boolean, branchId: String?): List<ScanReminder> =
            if (!shopping) all else all.filter { it.branchId != null && it.branchId == branchId }
    }
}
