package app.necessityq.domain

/**
 * Sample data taken from a real Microsoft To Do shopping list (exported Nov 2026):
 * items that were checked off become purchase history, open items become the current list.
 * Loading is idempotent: ids are deterministic and items already on the list are skipped.
 */
object TodoSample {
    data class Row(
        val name: String,
        val category: Category,
        val amount: Double?,
        val unit: QuantityUnit?,
        val note: String?,
        val atMillis: Long,
    ) {
        val quantity: Quantity? get() = if (amount != null && unit != null) Quantity(amount, unit) else null
        val identity: ItemIdentity get() = ItemIdentity(name, null, category)
    }

    /** Open tasks, with the time they were added to the To Do list. */
    val openLines: List<Row> = listOf(
        Row("פירות", Category.PRODUCE, null, null, null, 1794408547383L),
        Row("שימורים תירס גמדי", Category.PANTRY, 2.0, QuantityUnit.PIECE, null, 1794408453661L),
        Row("שימורים פטריות", Category.PANTRY, 2.0, QuantityUnit.PIECE, null, 1794408449024L),
        Row("בייגלה", Category.BREAKFAST_SNACKS, 3.0, QuantityUnit.PIECE, null, 1794408387149L),
        Row("פתיבר", Category.BREAKFAST_SNACKS, 2.0, QuantityUnit.PACKAGE, null, 1794408356706L),
        Row("שוקיים", Category.MEAT_FISH, 2.0, QuantityUnit.PIECE, null, 1794408350291L),
        Row("כרעיים", Category.MEAT_FISH, 1.0, QuantityUnit.PIECE, null, 1794408209337L),
        Row("כרובית סנפרוסט", Category.FROZEN, 2.0, QuantityUnit.PIECE, null, 1794408173968L),
        Row("קוטג'", Category.DAIRY, 2.0, QuantityUnit.PIECE, null, 1794408164307L),
        Row("חמאה 200 גרם", Category.DAIRY, 4.0, QuantityUnit.PIECE, null, 1794408097353L),
        Row("מריר", Category.BREAKFAST_SNACKS, 2.0, QuantityUnit.PACKAGE, null, 1794408073786L),
        Row("סוכר לבן", Category.PANTRY, 2.0, QuantityUnit.PIECE, null, 1794408063130L),
        Row("סוכר דמררה", Category.PANTRY, 1.0, QuantityUnit.PIECE, null, 1794408061085L),
        Row("פסטה ביסלי", Category.PANTRY, 2.0, QuantityUnit.PIECE, null, 1794408043711L),
        Row("שמן זית", Category.PANTRY, 2.0, QuantityUnit.PIECE, null, 1794407992452L),
        Row("כרוב", Category.PRODUCE, 2.0, QuantityUnit.PIECE, null, 1794352130284L),
        Row("חסה", Category.PRODUCE, 2.0, QuantityUnit.PIECE, null, 1794352123410L),
        Row("בטטות", Category.PRODUCE, 2.0, QuantityUnit.PIECE, null, 1794352100425L),
        Row("רוטב צ'ילי מתוק", Category.PANTRY, null, null, null, 1794352094791L),
        Row("סבון גוף שמפו לגברים עם משאבה", Category.TOILETRIES, null, null, null, 1794351408778L),
        Row("טישו", Category.CLEANING, 2.0, QuantityUnit.PACKAGE, null, 1794351392670L),
        Row("סקוטשים לכלים עם כסף וזהב", Category.CLEANING, null, null, null, 1794351385736L),
        Row("סבון כלים", Category.CLEANING, null, null, null, 1794351376585L),
        Row("קטשופ", Category.PANTRY, null, null, null, 1793720062164L),
        Row("חומץ", Category.PANTRY, null, null, null, 1793719729798L),
        Row("מלפפונים", Category.PRODUCE, null, null, null, 1793719588390L),
        Row("מיונז", Category.PANTRY, null, null, null, 1793719557352L),
    )

    /** Completed tasks, with the time they were checked off. */
    val purchases: List<Row> = listOf(
        Row("פלפל", Category.PRODUCE, null, null, null, 1793786219974L),
        Row("חטיפים לשבת", Category.BREAKFAST_SNACKS, null, null, null, 1793732054170L),
        Row("חלב", Category.DAIRY, null, null, null, 1793732054146L),
        Row("תפוחי אדמה", Category.PRODUCE, null, null, null, 1793785597691L),
        Row("קישואים", Category.PRODUCE, null, null, null, 1793732053555L),
        Row("קורנפלקס", Category.BREAKFAST_SNACKS, null, null, null, 1793732053385L),
        Row("טבעות בצל", Category.FROZEN, null, null, null, 1793732052984L),
        Row("פטריות בשימורים", Category.PANTRY, null, null, null, 1793732052840L),
        Row("מלפפון חמוץ", Category.PANTRY, null, null, null, 1793732052661L),
        Row("תירס גמדי", Category.PANTRY, null, null, null, 1793787463358L),
        Row("שוקולד ציפס", Category.BREAKFAST_SNACKS, 3.0, QuantityUnit.PIECE, null, 1793787463194L),
        Row("עגבניות", Category.PRODUCE, null, null, null, 1793732052280L),
        Row("עגבניות שרי תמר", Category.PRODUCE, null, null, null, 1793785532115L),
        Row("כרוב לבן", Category.PRODUCE, null, null, null, 1793732052104L),
        Row("מרקים", Category.PANTRY, null, null, null, 1793732051835L),
        Row("עבאדי", Category.PANTRY, null, null, "הסוג הקטן טעים יותר", 1793732051743L),
        Row("בייגלה", Category.BREAKFAST_SNACKS, null, null, null, 1793732051505L),
        Row("כרובית קפואה", Category.FROZEN, 3.0, QuantityUnit.PIECE, null, 1793732051363L),
        Row("צ'יפס קפוא", Category.FROZEN, null, null, null, 1793732051386L),
        Row("גיבנצ מגוררת", Category.DAIRY, null, null, null, 1793732050083L),
        Row("גבינה צהובה", Category.DAIRY, null, null, null, 1793732050193L),
        Row("קוטג'", Category.DAIRY, null, null, null, 1793732050307L),
        Row("גבינה", Category.DAIRY, null, null, null, 1793732050469L),
    )
}
