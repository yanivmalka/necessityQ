package app.necessityq.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pictures for item conversations: scaled down and JPEG-compressed so one picture fits easily
 * in a single database document (well under 1 MB).
 */
object ImageCodec {
    private const val MAX_SIDE = 1280
    const val MAX_BYTES = 700_000

    fun compress(bitmap: Bitmap): ByteArray {
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        } else bitmap
        var quality = 80
        while (true) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_BYTES || quality <= 30) return bytes
            quality -= 10
        }
    }

    /** Reads a picked image, sampling large photos down while decoding to save memory. */
    fun fromUri(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    fun decode(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}

/** Short voice messages: AAC, mono, low bit-rate — about 3 KB per second. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start() {
        stopQuietly()
        val target = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(24_000)
        r.setMaxDuration(MAX_MS.toInt())
        r.setOutputFile(target.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = target
        startedAt = System.currentTimeMillis()
    }

    /** Stops and returns (bytes, duration ms), or null if nothing usable was recorded. */
    fun stop(): Pair<ByteArray, Long>? {
        val r = recorder ?: return null
        val duration = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        val f = file
        file = null
        if (!ok || f == null || duration < 500) {
            f?.delete()
            return null
        }
        val bytes = f.readBytes()
        f.delete()
        return bytes to duration.coerceAtMost(MAX_MS)
    }

    fun stopQuietly() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        file?.delete()
        file = null
    }

    companion object {
        const val MAX_MS = 60_000L
    }
}

/** Plays one recording at a time. */
class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    var playingId: String? = null
        private set

    fun play(id: String, bytes: ByteArray, onDone: () -> Unit) {
        stop()
        val f = File(context.cacheDir, "play_$id.m4a")
        if (!f.exists() || f.length() != bytes.size.toLong()) f.writeBytes(bytes)
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener { stop(); onDone() }
            prepare()
            start()
        }
        playingId = id
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingId = null
    }
}
