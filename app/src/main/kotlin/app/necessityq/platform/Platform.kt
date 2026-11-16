package app.necessityq.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.necessityq.R
import app.necessityq.domain.ListSort

/** What a tap on a product tile does. */
enum class TapMode(val label: String, val hint: String) {
    INFO("נגיעה פותחת דף מידע", "פקדי הכמות מוצגים קבוע על כל פריט"),
    CONTROLS("נגיעה מציגה פקדים", "הפקדים מופיעים רק בנגיעה; דף המידע בכפתור ⓘ"),
}

/** Display toggles for the list ("what to show from the recommendation"). */
data class DisplayOptions(
    val showReason: Boolean = true,
    val showLastBought: Boolean = true,
    val showFrequency: Boolean = true,
    val showFavorite: Boolean = true,
    val showAddedBy: Boolean = false,
    val showPrices: Boolean = true,
    /** The requested quantity on the tile. */
    val showQuantity: Boolean = true,
    /** The +/− quantity buttons. */
    val showStepper: Boolean = true,
    /** Statistics on the tile (last bought, frequency). */
    val showStats: Boolean = true,
    /** Estimated total at the bottom of the list. */
    val showTotal: Boolean = true,
    /** Dairy, meat/fish and frozen grouped at the end of the list. */
    val chilledLast: Boolean = false,
    val tapMode: TapMode = TapMode.INFO,
)

/** Small device-local preferences: theme, sort, display toggles, current store, read markers. */
class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("necessityq-prefs", Context.MODE_PRIVATE)

    var brightTheme: Boolean
        get() = p.getBoolean("bright", true)
        set(v) = p.edit().putBoolean("bright", v).apply()

    var mode: app.necessityq.domain.ListMode
        get() = runCatching { app.necessityq.domain.ListMode.valueOf(p.getString("mode", null) ?: "") }
            .getOrDefault(app.necessityq.domain.ListMode.PLAN)
        set(v) = p.edit().putString("mode", v.name).apply()

    fun sortFor(mode: app.necessityq.domain.ListMode): ListSort =
        runCatching { ListSort.valueOf(p.getString("sort_" + mode.name, null) ?: "") }.getOrDefault(ListSort.GROUPS)

    fun setSortFor(mode: app.necessityq.domain.ListMode, v: ListSort) = p.edit().putString("sort_" + mode.name, v.name).apply()

    /** When the shop prompt was last answered; a new trip asks again after a few hours. */
    var storeAskedAt: Long
        get() = p.getLong("store_asked", 0L)
        set(v) = p.edit().putLong("store_asked", v).apply()

    var collapsed: Set<String>
        get() = p.getStringSet("collapsed", emptySet()).orEmpty()
        set(v) = p.edit().putStringSet("collapsed", v).apply()

    var currentStoreId: String?
        get() = p.getString("store", null)
        set(v) = p.edit().putString("store", v).apply()

    var display: DisplayOptions
        get() = DisplayOptions(
            showReason = p.getBoolean("d_reason", true),
            showLastBought = p.getBoolean("d_last", true),
            showFrequency = p.getBoolean("d_freq", true),
            showFavorite = p.getBoolean("d_fav", true),
            showAddedBy = p.getBoolean("d_by", false),
            showPrices = p.getBoolean("d_price", true),
            showQuantity = p.getBoolean("d_qty", true),
            showStepper = p.getBoolean("d_stepper", true),
            showStats = p.getBoolean("d_stats", true),
            showTotal = p.getBoolean("d_total", true),
            chilledLast = p.getBoolean("d_chilled", false),
            tapMode = runCatching { TapMode.valueOf(p.getString("d_tap", null) ?: "") }.getOrDefault(TapMode.INFO),
        )
        set(v) = p.edit()
            .putBoolean("d_reason", v.showReason).putBoolean("d_last", v.showLastBought)
            .putBoolean("d_freq", v.showFrequency).putBoolean("d_fav", v.showFavorite)
            .putBoolean("d_by", v.showAddedBy).putBoolean("d_price", v.showPrices)
            .putBoolean("d_qty", v.showQuantity).putBoolean("d_stepper", v.showStepper)
            .putBoolean("d_stats", v.showStats).putBoolean("d_total", v.showTotal)
            .putBoolean("d_chilled", v.chilledLast).putString("d_tap", v.tapMode.name).apply()

    /** Sort direction per stage of use: false = ascending. */
    fun descendingFor(mode: app.necessityq.domain.ListMode): Boolean = p.getBoolean("desc_" + mode.name, false)
    fun setDescendingFor(mode: app.necessityq.domain.ListMode, v: Boolean) = p.edit().putBoolean("desc_" + mode.name, v).apply()

    /** Play a sound when a new message arrives. */
    var chatSound: Boolean
        get() = p.getBoolean("chat_sound", true)
        set(v) = p.edit().putBoolean("chat_sound", v).apply()

    /** When the user last opened the conversation of [itemId] (millis). */
    fun readAt(itemId: String): Long = p.getLong("read_$itemId", 0L)
    fun markRead(itemId: String, atMillis: Long) = p.edit().putLong("read_$itemId", atMillis).apply()
}

/**
 * Local notifications. They fire while the app process is alive; true background push needs a server (FCM).
 * Two channels, because a channel's sound cannot change after it is created: with sound and quiet.
 */
class Notifier(private val context: Context) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(LOUD, "עדכוני הבית", NotificationManager.IMPORTANCE_HIGH))
            manager.createNotificationChannel(
                NotificationChannel(QUIET, "עדכוני הבית (שקט)", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
            )
        }
    }

    private fun allowed() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** A short chime inside the app (used when notifications are not allowed). */
    fun chime() {
        runCatching {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            android.media.RingtoneManager.getRingtone(context, uri)?.play()
        }
    }

    @SuppressLint("MissingPermission")
    fun notify(id: Int, title: String, text: String, sound: Boolean = true) {
        if (!allowed()) {
            if (sound) chime()
            return
        }
        val notification = NotificationCompat.Builder(context, if (sound) LOUD else QUIET)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private companion object {
        const val LOUD = "household"
        const val QUIET = "household_quiet"
    }
}

object LocationHelper {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Most recent known position from any provider (no Play Services needed), or null. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
