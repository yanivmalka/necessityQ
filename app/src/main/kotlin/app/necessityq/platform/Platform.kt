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

/** How a product tile opens the item's page. The quantity controls are a part of the tile ("תצוגה"). */
enum class TapMode(val label: String, val hint: String) {
    SINGLE("נגיעה אחת", "נגיעה בפריט פותחת את דף הפריט"),
    DOUBLE("נגיעה כפולה", "רק נגיעה כפולה פותחת את דף הפריט, כך שנגיעה בטעות לא פותחת אותו"),
    ;

    companion object {
        /** Stored names, including the older "נגיעה פותחת דף מידע / מציגה פקדים". */
        fun of(name: String?): TapMode = when (name) {
            "DOUBLE", "CONTROLS" -> DOUBLE
            else -> SINGLE
        }
    }
}

/** What a recommendation card says under the product's name: one choice. */
enum class RecommendationDetail(val label: String) {
    REASON("הסיבה (מומלץ)"),
    HISTORY("מתי נקנה ובאיזו תדירות"),
    NONE("כלום"),
}

/** Display toggles for the list and the recommendations. */
data class DisplayOptions(
    /** Recommendation cards: the reason, or when last bought and how often, or nothing. */
    val recommendationDetail: RecommendationDetail = RecommendationDetail.REASON,
    /** The ★ of favourites on the recommendation cards (a toggle of its own). */
    val showFavorite: Boolean = true,
    /** What a product tile shows while making the list. */
    val planTile: TileLayout = TileLayout.defaultFor(ListMode.PLAN),
    /** What a product tile shows in the shop. */
    val shopTile: TileLayout = TileLayout.defaultFor(ListMode.SHOP),
    /** Estimated total at the bottom of the list. */
    val showTotal: Boolean = true,
    /** Dairy, meat/fish and frozen grouped at the end of the list. */
    val chilledLast: Boolean = false,
    val tapMode: TapMode = TapMode.SINGLE,
    /** Items already checked off (struck through) at the bottom of the list. */
    val showBought: Boolean = true,
    /** Prices of products sold by weight: per kilo or per 100 grams. */
    val weightUnit: app.necessityq.domain.WeightPriceUnit = app.necessityq.domain.WeightPriceUnit.KILO,
) {
    fun tile(mode: ListMode): TileLayout = if (mode == ListMode.SHOP) shopTile else planTile

    fun withTile(mode: ListMode, layout: TileLayout): DisplayOptions =
        if (mode == ListMode.SHOP) copy(shopTile = layout) else copy(planTile = layout)
}

/** Small device-local preferences: theme, sort, display toggles, current store, read markers. */
class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("necessityq-prefs", Context.MODE_PRIVATE)

    /** Nothing was stored before this run: a new install (an update keeps what was stored). */
    private val freshInstall: Boolean = p.all.isEmpty()

    /** The order of the bottom-bar tabs (the first on the right). */
    var tabOrder: List<app.necessityq.domain.AppTab>
        get() = app.necessityq.domain.TabOrder.decode(p.getString("tab_order", null))
        set(v) = p.edit().putString("tab_order", app.necessityq.domain.TabOrder.encode(v)).apply()

    /** The tab the app opens on (the list, unless chosen otherwise). */
    var startTab: app.necessityq.domain.AppTab
        get() = app.necessityq.domain.AppTab.entries.firstOrNull { it.name == p.getString("start_tab", null) } ?: app.necessityq.domain.AppTab.LIST
        set(v) = p.edit().putString("start_tab", v.name).apply()

    /** Which features are on ("הגדרות" ← "יכולות"). */
    var features: app.necessityq.domain.Features
        get() = app.necessityq.domain.Features.decodeOff(p.getString("features_off", null))
        set(v) = p.edit().putString("features_off", v.encodeOff()).putBoolean("features_chosen", true).apply()

    /**
     * Whether the first-run questions were answered. Someone who used the app before the questions existed is not
     * asked: everything stays on for them. While the first run is replayed ([firstRunReplay]) they are asked again.
     */
    val featuresChosen: Boolean
        get() = firstRunReplay == null && (p.getBoolean("features_chosen", false) || !freshInstall)

    /** "הדגמת התחלה": the stage of the replayed first run ("ONBOARDING" or "QUESTIONS"); null = not replaying. */
    var firstRunReplay: String?
        get() = p.getString("first_run_replay", null)
        set(v) = p.edit().putString("first_run_replay", v).apply()

    /** The features that were on before the first run was replayed (restored when the replay is left early). */
    var replayBackup: String?
        get() = p.getString("replay_backup", null)
        set(v) = p.edit().putString("replay_backup", v).apply()

    /** The suggestions' memory ([app.necessityq.domain.AdvisorState]); it starts watching on the first read. */
    var advisor: app.necessityq.domain.AdvisorState
        get() = app.necessityq.domain.AdvisorState.decode(p.getString("advisor", null)).let {
            if (it.since > 0) it else it.copy(since = System.currentTimeMillis()).also { s -> advisor = s }
        }
        set(v) = p.edit().putString("advisor", v.encode()).apply()

    /** Recent times (millis, newest last, at most 20) of a usage event: "scan", "missing", "quick_close". */
    fun usageEvents(event: String): List<Long> =
        p.getString("u_$event", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun addUsageEvent(event: String, at: Long) =
        p.edit().putString("u_$event", (usageEvents(event) + at).takeLast(20).joinToString(",")).apply()

    fun clearUsage() {
        val e = p.edit()
        p.all.keys.filter { it.startsWith("u_") || it.startsWith("used_") || it.startsWith("enabled_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** When [feature] was last used (its tab or page opened), in millis; 0 = not since watching started. */
    fun usedAt(feature: app.necessityq.domain.Feature): Long = p.getLong("used_" + feature.name, 0L)
    fun markUsed(feature: app.necessityq.domain.Feature, at: Long) = p.edit().putLong("used_" + feature.name, at).apply()

    /** When [feature] was last turned on, in millis; 0 = unknown (on since before watching started). */
    fun enabledAt(feature: app.necessityq.domain.Feature): Long = p.getLong("enabled_" + feature.name, 0L)
    fun markEnabled(feature: app.necessityq.domain.Feature, at: Long) = p.edit().putLong("enabled_" + feature.name, at).apply()

    /** "תצוגה בהירה במיוחד": off by default, so the app follows the phone (light or dark). */
    var brightTheme: Boolean
        get() = p.getBoolean("bright", false)
        set(v) = p.edit().putBoolean("bright", v).apply()

    /** Colour palette of the app ([app.necessityq.ui.AppPalette] name). */
    var palette: String?
        get() = p.getString("palette", null)
        set(v) = p.edit().putString("palette", v).apply()

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
            recommendationDetail = recommendationDetail(),
            showFavorite = p.getBoolean("d_fav", true),
            planTile = tileLayout(ListMode.PLAN),
            shopTile = tileLayout(ListMode.SHOP),
            showTotal = p.getBoolean("d_total", true),
            chilledLast = p.getBoolean("d_chilled", false),
            tapMode = TapMode.of(p.getString("d_tap", null)),
            showBought = p.getBoolean("d_bought", true),
            weightUnit = runCatching { app.necessityq.domain.WeightPriceUnit.valueOf(p.getString("d_weight_unit", null) ?: "") }
                .getOrDefault(app.necessityq.domain.WeightPriceUnit.KILO),
        )
        set(v) = p.edit()
            .putString("d_rec_detail", v.recommendationDetail.name).putBoolean("d_fav", v.showFavorite)
            .putString("tile_" + ListMode.PLAN.name, v.planTile.encode())
            .putString("tile_" + ListMode.SHOP.name, v.shopTile.encode())
            .putBoolean("d_total", v.showTotal)
            .putBoolean("d_chilled", v.chilledLast).putString("d_tap", v.tapMode.name)
            .putBoolean("d_bought", v.showBought).putString("d_weight_unit", v.weightUnit.name).apply()

    /** Before it was one choice, three checkboxes: the reason won when it was on. */
    private fun recommendationDetail(): RecommendationDetail {
        p.getString("d_rec_detail", null)?.let { name -> RecommendationDetail.entries.firstOrNull { it.name == name }?.let { return it } }
        return when {
            p.getBoolean("d_reason", true) -> RecommendationDetail.REASON
            p.getBoolean("d_last", true) || p.getBoolean("d_freq", true) -> RecommendationDetail.HISTORY
            else -> RecommendationDetail.NONE
        }
    }

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

    /** Scanned products to be reminded of later ([app.necessityq.domain.ScanReminder] lines, newest last). */
    val remindLater: List<app.necessityq.domain.ScanReminder>
        get() = p.getString("remind_later", null)?.split('\n')?.mapNotNull { app.necessityq.domain.ScanReminder.decode(it) }.orEmpty()

    fun addRemindLater(reminder: app.necessityq.domain.ScanReminder) {
        saveRemindLater((remindLater.filterNot { it.code == reminder.code } + reminder).takeLast(100))
    }

    fun removeRemindLater(code: String) = saveRemindLater(remindLater.filterNot { it.code == code })

    private fun saveRemindLater(list: List<app.necessityq.domain.ScanReminder>) =
        p.edit().putString("remind_later", list.joinToString("\n") { it.encode() }).apply()

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

    /** The branch whose prices the list showed last: its cached prices are read the moment the app starts. */
    var lastPriceBranch: String?
        get() = p.getString("last_price_branch", null)
        set(v) = p.edit().putString("last_price_branch", v).apply()

    /** Before a trip: the saving (₪) that makes another supermarket (or one more stop) worth it. */
    var tripThreshold: Int
        get() = p.getInt("trip_threshold", 50)
        set(v) = p.edit().putInt("trip_threshold", v).apply()

    /** Before a trip: how many supermarkets the list may be split over (1 = no split). */
    var tripMaxStores: Int
        get() = p.getInt("trip_max_stores", 1)
        set(v) = p.edit().putInt("trip_max_stores", v).apply()

    /** Promotions of customer clubs, credit cards and coupons count too. */
    var tripMembers: Boolean
        get() = p.getBoolean("trip_members", false)
        set(v) = p.edit().putBoolean("trip_members", v).apply()

    /** The household's regular supermarket (a followed branch id); null = the first followed branch. */
    var tripHome: String?
        get() = p.getString("trip_home", null)
        set(v) = p.edit().putString("trip_home", v).apply()

    /** The split the trip follows: "itemId=branchId" per line, empty when the trip is not split. */
    var tripAssignment: Map<String, String>
        get() = p.getStringSet("trip_assignment", emptySet()).orEmpty().mapNotNull { e ->
            val i = e.lastIndexOf('=')
            if (i <= 0) null else e.substring(0, i) to e.substring(i + 1)
        }.toMap()
        set(v) = p.edit().putStringSet("trip_assignment", v.map { (k, b) -> "$k=$b" }.toSet()).apply()

    /** The stops of the split, in order. */
    var tripStops: List<String>
        get() = p.getString("trip_stops", "").orEmpty().split(',').filter { it.isNotBlank() }
        set(v) = p.edit().putString("trip_stops", v.joinToString(",")).apply()

    /** Look for a new version every time the app opens. */
    var autoCheckUpdates: Boolean
        get() = p.getBoolean("upd_auto_check", true)
        set(v) = p.edit().putBoolean("upd_auto_check", v).apply()

    /** Open the installer as soon as an update finishes downloading. */
    var autoInstallUpdates: Boolean
        get() = p.getBoolean("upd_auto_install", false)
        set(v) = p.edit().putBoolean("upd_auto_install", v).apply()

    /** How many times the swipe hint above the list was shown (it stops after three). */
    var swipeHintSeen: Int
        get() = p.getInt("swipe_hint_seen", 0)
        set(v) = p.edit().putInt("swipe_hint_seen", v).apply()

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
