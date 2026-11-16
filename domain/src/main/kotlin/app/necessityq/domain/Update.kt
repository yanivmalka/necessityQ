package app.necessityq.domain

/** A published build of the app (a GitHub Release with an APK asset). */
data class ReleaseInfo(
    val tag: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
) {
    val versionName: String get() = tag.trim().trimStart('v', 'V')
}

object Releases {
    /**
     * CI tags every build `v0.1.<run number>` and uses the run number as the Android versionCode,
     * so the last number of the tag is the comparable build number.
     */
    fun versionCodeOf(tag: String): Int? = tag.trim().trimStart('v', 'V').substringAfterLast('.').toIntOrNull()

    fun isNewer(latestCode: Int, currentCode: Int): Boolean = latestCode > currentCode
}
