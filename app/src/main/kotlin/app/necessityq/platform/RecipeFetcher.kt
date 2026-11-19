package app.necessityq.platform

import app.necessityq.domain.ImportedRecipe
import app.necessityq.domain.RecipeImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** Downloads a recipe page and reads the recipe on it (see [RecipeImport]). */
object RecipeFetcher {
    private const val MAX_BYTES = 4_000_000

    suspend fun fetch(url: String): ImportedRecipe = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            // some recipe sites refuse clients that do not look like a browser
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "he-IL,he;q=0.9,en;q=0.5")
        }
        try {
            check(connection.responseCode == 200) { "הדף לא נפתח (${connection.responseCode})" }
            val charset = connection.contentType?.substringAfter("charset=", "")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                while (out.size() < MAX_BYTES) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            RecipeImport.fromHtml(String(bytes, charset), connection.url.toString())
                ?: error("לא נמצא מתכון בדף הזה. נסו קישור לדף של מתכון אחד.")
        } finally {
            connection.disconnect()
        }
    }
}
