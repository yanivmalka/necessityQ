package app.necessityq.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.necessityq.R

/**
 * צ'אט פוד: the alert of a message aimed at me (I was tagged with "@", or my message was quoted).
 *
 * It arrives two ways — as a push from the server (also when the app is closed) and from the live
 * conversation while the app runs — so each message alerts once ([alert] ignores repeats).
 *
 * The sound is heard even when the phone is on silent or vibrate: it is played on the alarm stream,
 * which the ringer switch does not mute (the alarm volume applies). The notification itself is posted
 * on its own silent channel so the sound is not played twice.
 */
object MentionAlerter {
    private const val CHANNEL = "chat_mentions"

    const val EXTRA_ITEM_ID = "open_chat_item_id"
    const val EXTRA_ITEM_NAME = "open_chat_item_name"

    /** Set by the app: the conversation that is open on screen right now (no alert for it). */
    @Volatile var openItemId: String? = null

    /** Set by the activity: whether the app is on screen. */
    @Volatile var appVisible: Boolean = false

    private val alerted = LinkedHashSet<String>()
    private const val REMEMBER = 300

    private fun firstTime(messageId: String): Boolean = synchronized(alerted) {
        if (!alerted.add(messageId)) return false
        if (alerted.size > REMEMBER) alerted.remove(alerted.first())
        true
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "צ'אט פוד: תיוגים וציטוטים", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "כשמתייגים אותך ב-@ או מצטטים הודעה שלך"
                setSound(null, null) // played by the app on the alarm stream, see [playSound]
                enableVibration(true)
                setBypassDnd(true) // honoured only if the user lets the app through "Do not disturb"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    /**
     * Alerts about one message. [loud] = heard even on silent (the user's choice, on by default).
     * Nothing happens when the same message was already alerted, or its conversation is open on screen.
     */
    @SuppressLint("MissingPermission")
    fun alert(
        context: Context,
        messageId: String,
        itemId: String,
        itemName: String,
        title: String,
        text: String,
        loud: Boolean,
    ) {
        if (appVisible && openItemId == itemId) return
        if (!firstTime(messageId)) return
        ensureChannel(context)
        playSound(context, loud)
        vibrate(context)

        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = Intent(context, app.necessityq.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_ITEM_NAME, itemName)
        }
        val tap = PendingIntent.getActivity(
            context, itemId.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(messageId.hashCode(), notification) }
    }

    /** The phone's notification sound; on the alarm stream when [loud], so silent / vibrate mode does not mute it. */
    private fun playSound(context: Context, loud: Boolean) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return
            val attributes = AudioAttributes.Builder()
                .setUsage(if (loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context.applicationContext, uri)
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
                prepare()
                start()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220), -1))
        }
    }
}
