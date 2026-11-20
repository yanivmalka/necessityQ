package app.necessityq.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import app.necessityq.R

/**
 * The app icons the user can choose from ("מראה"). Each is a launcher `activity-alias` in the manifest with its own
 * icon; choosing one enables its alias and disables the others, so the launcher shows that icon.
 */
enum class AppIcon(val alias: String, val label: String, @DrawableRes val foreground: Int) {
    DEFAULT(".IconDefault", "רשימה, עגלה וחץ", R.drawable.ic_launcher_list_badge_cycle_foreground),
    LIST_BADGE(".IconListBadge", "רשימה ועגלה", R.drawable.ic_launcher_list_badge_foreground),
    CART_CYCLE(".IconCartCycle", "עגלה וחץ", R.drawable.ic_launcher_cart_cycle_foreground),
    CART_LIST(".IconCartList", "עגלה-רשימה", R.drawable.ic_launcher_cart_list_foreground),
    BASKET(".IconBasket", "סל קניות", R.drawable.ic_launcher_basket_foreground),
}

object AppIcons {
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, "app.necessityq" + icon.alias)

    /** The icon the launcher shows now. */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DEFAULT // as declared in the manifest
                else -> false
            }
        } ?: AppIcon.DEFAULT
    }

    /** Switches the launcher icon. The new one is enabled first, so the app always has a launcher entry. */
    fun set(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
