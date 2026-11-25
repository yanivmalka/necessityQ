package app.necessityq.domain

/**
 * "הדגמת התחלה" (in the account page): replays the first run — the opening screen, the welcome page and the
 * first-run questions — as a new user would see it, without creating or deleting anything. Offered only to the
 * accounts below. They are kept as SHA-256 hashes of the lower-case address, so the source does not carry the
 * addresses themselves.
 */
object FirstRunDemo {
    private val ALLOWED = setOf(
        "40c85837a7aba20d4fef2b365d7a3dbd4280213df5fe0f42cab6396860a9dccc",
        "350f6c7a35213facdb808bb1f3ba05f96368052542ee26a95c65ef1513d764fc",
    )

    fun allowed(email: String?): Boolean = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { sha256(it) in ALLOWED } == true

    internal fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
