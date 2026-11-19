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
import app.necessityq.domain.ListMode
import app.necessityq.domain.ListSort
import app.necessityq.domain.TileElement
import app.necessityq.domain.TileLayout
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** What a tap on a product tile does. */
enum class TapMode(val label: String, val hint: String) {
    INFO("נגיעה פותחת דף מידע", "פקדי הכמות מוצגים קבוע על כל פריט"),
    CONTROLS("נגיעה מציגה פקדים", "הפקדים מופיעים רק בנגיעה; דף המידע בכפתור ⓘ"),
}

/** Display toggles for the list and the recommendations. */
data class DisplayOptions(
    /** Recommendation cards: why it is suggested, when last bought, how often, the ★. */
    val showReason: Boolean = true,
    val showLastBought: Boolean = true,
    val showFrequency: Boolean = true,
    val showFavorite: Boolean = true,
    /** What a product tile shows while making the list. */
    val planTile: TileLayout = TileLayout.defaultFor(ListMode.PLAN),
    /** What a product tile shows in the shop. */
    val shopTile: TileLayout = TileLayout.defaultFor(ListMode.SHOP),
    /** Estimated total at the bottom of the list. */
    val showTotal: Boolean = true,
    /** Dairy, meat/fish and frozen grouped at the end of the list. */
    val chilledLast: Boolean = false,
    val tapMode: TapMode = TapMode.INFO,
    /** Items already checked off (struck through) at the bottom of the list. */
    val showBought: Boolean = true,
) {
    fun tile(mode: ListMode): TileLayout = if (mode == ListMode.SHOP) shopTile else planTile

    fun withTile(mode: ListMode, layout: TileLayout): DisplayOptions =
        if (mode == ListMode.SHOP) copy(shopTile = layout) else copy(planTile = layout)
}

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
            planTile = tileLayout(ListMode.PLAN),
            shopTile = tileLayout(ListMode.SHOP),
            showTotal = p.getBoolean("d_total", true),
            chilledLast = p.getBoolean("d_chilled", false),
            tapMode = runCatching { TapMode.valueOf(p.getString("d_tap", null) ?: "") }.getOrDefault(TapMode.INFO),
            showBought = p.getBoolean("d_bought", true),
        )
        set(v) = p.edit()
            .putBoolean("d_reason", v.showReason).putBoolean("d_last", v.showLastBought)
            .putBoolean("d_freq", v.showFrequency).putBoolean("d_fav", v.showFavorite)
            .putString("tile_" + ListMode.PLAN.name, v.planTile.encode())
            .putString("tile_" + ListMode.SHOP.name, v.shopTile.encode())
            .putBoolean("d_total", v.showTotal)
            .putBoolean("d_chilled", v.chilledLast).putString("d_tap", v.tapMode.name)
            .putBoolean("d_bought", v.showBought).apply()

    /**
     * The tile of [mode]; before tiles were set per stage, one set of checkboxes covered both, so a phone
     * that never saved a layout starts from those choices.
     */
    private fun tileLayout(mode: ListMode): TileLayout {
        TileLayout.decode(p.getString("tile_" + mode.name, null))?.let { return it }
        var shown = TileLayout.defaultFor(mode).shown
        fun off(key: String, default: Boolean, vararg elements: TileElement) {
            if (!p.getBoolean(key, default)) shown = shown - elements.toSet()
        }
        off("d_qty", true, TileElement.QUANTITY)
        off("d_stepper", true, TileElement.STEPPER)
        off("d_price", true, TileElement.PRICE)
        off("d_stats", true, TileElement.LAST_BOUGHT, TileElement.FREQUENCY)
        off("d_last", true, TileElement.LAST_BOUGHT)
        off("d_freq", true, TileElement.FREQUENCY)
        off("d_fav", true, TileElement.FAVORITE)
        off("d_weight", true, TileElement.WEIGHT)
        if (p.getBoolean("d_by", false)) shown = shown + TileElement.ADDED_BY
        return TileLayout(shown)
    }

    /** Sort direction per stage of use: false = ascending. */
    fun descendingFor(mode: app.necessityq.domain.ListMode): Boolean = p.getBoolean("desc_" + mode.name, false)
    fun setDescendingFor(mode: app.necessityq.domain.ListMode, v: Boolean) = p.edit().putBoolean("desc_" + mode.name, v).apply()

    /** Scanned products to be reminded of later ("code\tname\tprice\tbranch\tat" per entry, newest last). */
    val remindLater: List<String>
        get() = p.getString("remind_later", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun addRemindLater(code: String, name: String, price: Double?, branchId: String?) {
        val entry = listOf(code, name.replace('\t', ' ').replace('\n', ' '), price?.toString().orEmpty(), branchId.orEmpty(), System.currentTimeMillis().toString())
            .joinToString("\t")
        val next = (remindLater.filterNot { it.startsWith(code + "\t") } + entry).takeLast(100)
        p.edit().putString("remind_later", next.joinToString("\n")).apply()
    }

    /** Play a sound when a new message arrives. */
    var chatSound: Boolean
        get() = p.getBoolean("chat_sound", true)
        set(v) = p.edit().putBoolean("chat_sound", v).apply()

    /** צ'אט פוד: notify on every message, or only when I am tagged / quoted (the default). */
    var chatNotifyMode: app.necessityq.domain.ChatNotifyMode
        get() = p.getString("chat_notify_mode", null)
            ?.let { runCatching { app.necessityq.domain.ChatNotifyMode.valueOf(it) }.getOrNull() }
            ?: app.necessityq.domain.ChatNotifyMode.TAGGED_ONLY
        set(v) = p.edit().putString("chat_notify_mode", v.name).apply()

    /** A tag or a quote is heard even when the phone is on silent (played on the alarm stream). */
    var loudMentions: Boolean
        get() = p.getBoolean("loud_mentions", true)
        set(v) = p.edit().putBoolean("loud_mentions", v).apply()

    /** Playback speed of voice messages (1, 1.5 or 2). */
    var voiceSpeed: Float
        get() = p.getFloat("voice_speed", 1f)
        set(v) = p.edit().putFloat("voice_speed", v).apply()

    /** Look for a new version every time the app opens. */
    var autoCheckUpdates: Boolean
        get() = p.getBoolean("upd_auto_check", true)
        set(v) = p.edit().putBoolean("upd_auto_check", v).apply()

    /** Open the installer as soon as an update finishes downloading. */
    var autoInstallUpdates: Boolean
        get() = p.getBoolean("upd_auto_install", false)
        set(v) = p.edit().putBoolean("upd_auto_install", v).apply()

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

    /** [openItemId]: a tap opens that product's conversation (צ'אט פוד); null = a tap opens the app. */
    @SuppressLint("MissingPermission")
    fun notify(id: Int, title: String, text: String, sound: Boolean = true, openItemId: String? = null, openItemName: String? = null) {
        if (!allowed()) {
            if (sound) chime()
            return
        }
        val open = android.content.Intent(context, app.necessityq.ui.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openItemId != null) {
                putExtra(MentionAlerter.EXTRA_ITEM_ID, openItemId)
                putExtra(MentionAlerter.EXTRA_ITEM_NAME, openItemName.orEmpty())
            }
        }
        val tap = android.app.PendingIntent.getActivity(
            context, openItemId?.hashCode() ?: 0, open,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, if (sound) LOUD else QUIET)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Ongoing notification of the update download; [percent] null = size unknown yet. */
    @SuppressLint("MissingPermission")
    fun updateProgress(versionName: String, percent: Int?) {
        if (!allowed()) return
        val notification = NotificationCompat.Builder(context, QUIET)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("מוריד עדכון $versionName")
            .setContentText(percent?.let { "$it%" } ?: "מתחיל…")
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, notification) }
    }

    /** Replaces the progress notification with "ready"; a tap opens the installer. */
    @SuppressLint("MissingPermission")
    fun updateReady(versionName: String, install: android.content.Intent) {
        if (!allowed()) return
        val tap = android.app.PendingIntent.getActivity(
            context, UPDATE_ID, install,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, QUIET)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("העדכון $versionName הורד")
            .setContentText("נגיעה להתקנה")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, notification) }
    }

    fun cancelUpdate() {
        runCatching { NotificationManagerCompat.from(context).cancel(UPDATE_ID) }
    }

    private companion object {
        const val UPDATE_ID = 0x0D0A7E
        const val LOUD = "household"
        const val QUIET = "household_quiet"
    }
}

object LocationHelper {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Location is switched on in the device settings (some provider is enabled). */
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching { manager.getProviders(true).any { it != LocationManager.PASSIVE_PROVIDER } }.getOrDefault(false)
    }

    /** Most recent known position from any provider (no Play Services needed), or null. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * A fresh position: a recent, accurate last-known fix is used as is; otherwise every enabled
     * provider (fused, network, GPS) is asked for one fix and the first answer wins. Falls back to the
     * last known position when nothing answers within [timeoutMs]. Works without Play Services.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 10_000): Location? {
        if (!hasPermission(context)) return null
        val last = lastKnown(context)
        val ageMs = last?.let { (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
        if (last != null && ageMs != null && ageMs < 60_000 && last.hasAccuracy() && last.accuracy <= 60f) return last

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled) add(LocationManager.FUSED_PROVIDER)
            if (LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
            if (LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
        }
        if (providers.isEmpty()) return last
        val fresh = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.flow.channelFlow {
                providers.forEach { provider -> launch { singleFix(context, manager, provider)?.let { send(it) } } }
            }.firstOrNull()
        }
        return fresh ?: last
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun singleFix(context: Context, manager: LocationManager, provider: String): Location? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val resume: (Location?) -> Unit = { location -> if (cont.isActive) cont.resumeWith(Result.success(location)) }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancel = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { resume(it) }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) = resume(location)
                        override fun onProviderDisabled(provider: String) = resume(null)
                        override fun onProviderEnabled(provider: String) {}
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    }
                    manager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                }
            } catch (e: Exception) {
                resume(null)
            }
        }
}
