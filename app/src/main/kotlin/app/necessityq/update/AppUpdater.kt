package app.necessityq.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.necessityq.domain.ReleaseInfo
import app.necessityq.domain.Releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from GitHub Releases: asks the public Releases API for the newest build, downloads its
 * APK into the app cache and hands it to the system installer. Updates install over the existing app
 * because every build is signed with the same key.
 */
class AppUpdater(private val context: Context, private val repo: String = "yanivmalka/necessityQ") {

    private val updatesDir get() = File(context.cacheDir, "updates")

    /** The newest published release, or null when it has no APK. */
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = open("https://api.github.com/repos/$repo/releases/latest")
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            check(connection.responseCode == 200) { "בדיקת העדכון נכשלה (${connection.responseCode})" }
            val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val tag = json.getString("tag_name")
            val code = Releases.versionCodeOf(tag) ?: return@withContext null
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            ReleaseInfo(
                tag = tag,
                versionCode = code,
                notes = json.optString("body").trim(),
                apkUrl = apk.getString("browser_download_url"),
                sizeBytes = apk.optLong("size"),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the APK, reporting progress in 0..1, and returns the file. */
    suspend fun download(info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        updatesDir.mkdirs()
        updatesDir.listFiles()?.forEach { it.delete() }
        val target = File(updatesDir, "necessityQ-${info.versionCode}.apk")
        val connection = open(info.apkUrl) // follows the redirect to the release CDN
        try {
            check(connection.responseCode == 200) { "ההורדה נכשלה (${connection.responseCode})" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            check(info.sizeBytes <= 0 || target.length() == info.sizeBytes) { "הקובץ שהורד פגום, נסו שוב" }
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install updates. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "necessityQ-updater")
    }
}
