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

/** Display toggles for the list ("what to show from the recommendation"). */
data class DisplayOptions(
    val showReason: Boolean = true,
    val showLastBought: Boolean = true,
    val showFrequency: Boolean = true,
    val showFavorite: Boolean = true,
    val showAddedBy: Boolean = false,
    val showPrices: Boolean = true,
)

/** Small device-local preferences: theme, sort, display toggles, current store, read markers. */
class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("necessityq-prefs", Context.MODE_PRIVATE)

    var brightTheme: Boolean
        get() = p.getBoolean("bright", true)
        set(v) = p.edit().putBoolean("bright", v).apply()

    var sort: ListSort
        get() = runCatching { ListSort.valueOf(p.getString("sort", null) ?: "") }.getOrDefault(ListSort.GROUPS)
        set(v) = p.edit().putString("sort", v.name).apply()

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
        )
        set(v) = p.edit()
            .putBoolean("d_reason", v.showReason).putBoolean("d_last", v.showLastBought)
            .putBoolean("d_freq", v.showFrequency).putBoolean("d_fav", v.showFavorite)
            .putBoolean("d_by", v.showAddedBy).putBoolean("d_price", v.showPrices).apply()

    /** When the user last opened the conversation of [itemId] (millis). */
    fun readAt(itemId: String): Long = p.getLong("read_$itemId", 0L)
    fun markRead(itemId: String, atMillis: Long) = p.edit().putLong("read_$itemId", atMillis).apply()
}

/** Local notifications. They fire while the app process is alive; true background push needs a server (FCM). */
class Notifier(private val context: Context) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("household", "עדכוני הבית", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun notify(id: Int, title: String, text: String) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val notification = NotificationCompat.Builder(context, "household")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
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
