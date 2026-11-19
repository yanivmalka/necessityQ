package app.necessityq.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pictures for item conversations: scaled down and JPEG-compressed so one picture fits easily
 * in a single database document (well under 1 MB).
 */
object ImageCodec {
    private const val MAX_SIDE = 1600
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

    /**
     * Reads a picked or photographed image, sampling large photos down while decoding to save memory,
     * and turning it upright (camera photos are usually stored sideways with a rotation tag).
     */
    fun fromUri(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        upright(bitmap, orientation)
    }.getOrNull()

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * A fresh file the camera app writes the full-size photo into (the preview contract only returns a
     * thumbnail of about 200 px, which looked blurry once shown at full width).
     */
    fun newCameraFile(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun decode(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}

/**
 * Profile pictures: a centred square, 256 px, JPEG — about 15 KB, Base64-encoded so it fits in the
 * member's profile document and every household member sees it without extra downloads.
 */
object AvatarCodec {
    private const val SIDE = 256

    fun encode(bitmap: Bitmap): String {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > SIDE) Bitmap.createScaledBitmap(square, SIDE, SIDE, true) else square
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    fun decode(photo: String): Bitmap? = runCatching {
        val bytes = android.util.Base64.decode(photo, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** Downloads a Google account picture at a size that suits [SIDE] and encodes it; null when it fails. */
    fun fromUrl(url: String): String? = runCatching {
        // Google picture URLs end with a size directive such as "=s96-c"
        val sized = if (Regex("=s\\d+(-c)?$").containsMatchIn(url)) url.replace(Regex("=s\\d+(-c)?$"), "=s${SIDE}-c") else url
        val connection = (java.net.URL(sized).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == 200)
            val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            encode(bitmap)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** Short voice messages: AAC, mono, low bit-rate — about 3 KB per second. */
class VoiceRecorder(private val context: Context) {
    class Recording(val bytes: ByteArray, val durationMs: Long, val waveform: List<Int>)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = ArrayList<Int>()

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
        levels.clear()
    }

    /** Call about every [SAMPLE_MS] while recording: remembers the loudness for the waveform. */
    fun sample() {
        val level = runCatching { recorder?.maxAmplitude }.getOrNull() ?: return
        levels.add(level)
    }

    /** Stops and returns the recording, or null if nothing usable was recorded. */
    fun stop(): Recording? {
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
        return Recording(bytes, duration.coerceAtMost(MAX_MS), app.necessityq.domain.Waveform.fromAmplitudes(levels))
    }

    fun stopQuietly() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        file?.delete()
        file = null
    }

    companion object {
        const val MAX_MS = 60_000L
        const val SAMPLE_MS = 100L
    }
}

/**
 * Plays one recording at a time, with pause / resume, seeking and speed (1×, 1.5×, 2×).
 * A paused recording stays loaded, so play continues where it stopped.
 */
class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var onDone: (() -> Unit)? = null

    /** The recording that is loaded (playing or paused), or null. */
    var playingId: String? = null
        private set

    /** Playback speed for every recording; applied at once to the one that plays. */
    var speed: Float = 1f
        private set

    val isPlaying: Boolean get() = runCatching { player?.isPlaying }.getOrNull() == true

    fun play(id: String, bytes: ByteArray, startMs: Long = 0L, onDone: () -> Unit) {
        stop()
        val f = File(context.cacheDir, "play_$id.m4a")
        if (!f.exists() || f.length() != bytes.size.toLong()) f.writeBytes(bytes)
        val p = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener {
                val done = this@VoicePlayer.onDone
                this@VoicePlayer.stop()
                done?.invoke()
            }
            prepare()
            if (startMs > 0) seekTo(startMs.toInt())
            start()
        }
        player = p
        playingId = id
        this.onDone = onDone
        applySpeed(p)
    }

    fun pause() { runCatching { player?.pause() } }

    /** Continues a paused recording; false when nothing is loaded. */
    fun resume(): Boolean {
        val p = player ?: return false
        return runCatching { p.start(); applySpeed(p) }.isSuccess
    }

    fun setSpeed(value: Float) {
        speed = value
        // changing the speed of a paused player would start it, so only a playing one is touched
        player?.takeIf { isPlaying }?.let { applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    /** Playback position in ms (0 when nothing is loaded). */
    fun position(): Long = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L

    fun seekTo(ms: Int) { runCatching { player?.seekTo(ms) } }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingId = null
        onDone = null
    }
}
