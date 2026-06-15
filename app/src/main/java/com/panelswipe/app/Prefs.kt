package com.panelswipe.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Thin wrapper over SharedPreferences holding the user's PanelSwipe settings.
 *
 * Values are read live by [SwipeAccessibilityService] on every gesture, so the
 * service always honours whatever the settings screen last wrote.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Master on/off. When false the service ignores all gestures. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * Position of the hot-zone divider as a fraction of screen width (0..1).
     * Touches starting left of this open one panel, right of it the other.
     * Set this to match the divider you configured in QuickStar.
     */
    var dividerFraction: Float
        get() = sp.getFloat(KEY_DIVIDER, DEFAULT_DIVIDER)
        set(value) = sp.edit().putFloat(KEY_DIVIDER, value.coerceIn(0.05f, 0.95f)).apply()

    /**
     * When false: left = Notifications, right = Quick Settings (Samsung default).
     * When true: the sides are swapped.
     */
    var swapSides: Boolean
        get() = sp.getBoolean(KEY_SWAP, false)
        set(value) = sp.edit().putBoolean(KEY_SWAP, value).apply()

    /** Minimum downward travel (in dp) before a touch counts as a swipe. */
    var minSwipeDistanceDp: Int
        get() = sp.getInt(KEY_MIN_DISTANCE, DEFAULT_MIN_DISTANCE_DP)
        set(value) = sp.edit().putInt(KEY_MIN_DISTANCE, value.coerceIn(16, 200)).apply()

    companion object {
        const val FILE_NAME = "panelswipe_prefs"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_DIVIDER = "divider_fraction"
        private const val KEY_SWAP = "swap_sides"
        private const val KEY_MIN_DISTANCE = "min_swipe_distance_dp"

        const val DEFAULT_DIVIDER = 0.5f
        const val DEFAULT_MIN_DISTANCE_DP = 64

        /**
         * Returns true if PanelSwipe's accessibility service is currently
         * enabled by the user. Reads the secure setting directly so it is
         * accurate even right after the user toggles it in system settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, SwipeAccessibilityService::class.java)
                .flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            for (component in splitter) {
                if (component.equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
