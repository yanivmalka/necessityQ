package app.necessityq.domain

/** A published build of the app (a GitHub Release with an APK asset). */
data class ReleaseInfo(
    val tag: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
) {
    val versionName: String get() = Releases.versionNameOf(tag)
}

object Releases {
    private val buildSuffix = Regex("-b(\\d+)$")

    /**
     * CI tags every build `v<major.minor>.<n>-b<build>`: `n` restarts from 0 whenever the release line
     * changes, so the Android versionCode (the run number + 200, which only rises) follows the `-b`.
     * Older tags, `v0.1.<build>`, carry the build as the last number.
     */
    fun versionCodeOf(tag: String): Int? {
        val clean = tag.trim()
        buildSuffix.find(clean)?.let { return it.groupValues[1].toIntOrNull() }
        return clean.trimStart('v', 'V').substringAfterLast('.').toIntOrNull()
    }

    /** The name shown to people: the tag without the `v` and the `-b<build>` suffix. */
    fun versionNameOf(tag: String): String = tag.trim().trimStart('v', 'V').replace(buildSuffix, "")

    fun isNewer(latestCode: Int, currentCode: Int): Boolean = latestCode > currentCode
}
