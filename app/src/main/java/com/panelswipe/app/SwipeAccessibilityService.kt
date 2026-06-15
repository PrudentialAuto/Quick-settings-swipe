package com.panelswipe.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Extends the Notifications-vs-Quick-Settings "hot zone" to the home screen.
 *
 * How it works:
 *  - We register for touchscreen [MotionEvent]s via the accessibility framework
 *    ([android.accessibilityservice.AccessibilityServiceInfo.setMotionEventSources]).
 *    These events are *observed only* — they are still delivered to the launcher,
 *    so app icons, taps, long-presses and page swipes keep working untouched.
 *  - We only act while a launcher (home screen) is in the foreground, tracked
 *    via TYPE_WINDOW_STATE_CHANGED events.
 *  - When the user makes a downward swipe, we look at where it STARTED on the X
 *    axis. Left of the configured divider opens one panel, right of it the other,
 *    via [performGlobalAction] — exactly the panels the system itself opens.
 *
 * There is no overlay window, no foreground service, no polling and no wake
 * locks: the service simply reacts to touch callbacks the framework already
 * produces, so its battery impact is negligible and it is completely invisible.
 */
class SwipeAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs

    /** Packages that act as a home screen / launcher on this device. */
    private var homePackages: Set<String> = emptySet()

    /** Package of the window currently in the foreground. */
    private var foregroundPackage: String? = null

    // ---- Per-gesture tracking state ----
    private var tracking = false
    private var triggeredThisGesture = false
    private var startX = 0f
    private var startY = 0f
    private var screenWidthPx = 1
    private var statusBarHeightPx = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        homePackages = resolveHomePackages()
        statusBarHeightPx = resolveStatusBarHeight()

        // Ask the framework to deliver touchscreen motion events to us. This is
        // configured purely at runtime (there is no XML attribute for it):
        // FLAG_SEND_MOTION_EVENTS opts in, and setMotionEventSources picks which
        // input sources we want. The events are observed only, never consumed.
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS
            motionEventSources = InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { foregroundPackage = it.toString() }
        }
    }

    override fun onInterrupt() {
        tracking = false
    }

    override fun onMotionEvent(event: MotionEvent) {
        // Cheap guard rails first so we do almost nothing while off-home.
        if (!::prefs.isInitialized || !prefs.enabled) return
        if (event.source and InputDevice.SOURCE_TOUCHSCREEN != InputDevice.SOURCE_TOUCHSCREEN) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchDown(event)
            MotionEvent.ACTION_MOVE -> onTouchMove(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
    }

    private fun onTouchDown(event: MotionEvent) {
        // Only begin tracking when the home screen is showing.
        if (!isOnHome()) {
            tracking = false
            return
        }
        val downY = event.rawY
        // Ignore swipes that start inside the system status bar: the OS already
        // applies its own hot zone there, and we don't want to double-trigger.
        if (downY < statusBarHeightPx) {
            tracking = false
            return
        }
        startX = event.rawX
        startY = downY
        screenWidthPx = currentScreenWidthPx()
        tracking = true
        triggeredThisGesture = false
    }

    private fun onTouchMove(event: MotionEvent) {
        if (!tracking || triggeredThisGesture) return

        val dy = event.rawY - startY
        val dx = event.rawX - startX
        val minPx = dpToPx(prefs.minSwipeDistanceDp.toFloat())

        // Must be a clearly downward, mostly-vertical drag.
        if (dy < minPx) return
        if (dy <= kotlin.math.abs(dx)) return

        triggeredThisGesture = true

        val fraction = startX / screenWidthPx.coerceAtLeast(1)
        val leftZone = fraction < prefs.dividerFraction

        // Default: left = Notifications, right = Quick Settings.
        val openNotifications = leftZone != prefs.swapSides
        val action = if (openNotifications) {
            GLOBAL_ACTION_NOTIFICATIONS
        } else {
            GLOBAL_ACTION_QUICK_SETTINGS
        }
        performGlobalAction(action)
    }

    private fun isOnHome(): Boolean {
        val pkg = foregroundPackage ?: return false
        return pkg in homePackages
    }

    /** All installed launchers, so we recognise whichever one is the home screen. */
    private fun resolveHomePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun currentScreenWidthPx(): Int {
        val wm = getSystemService(WindowManager::class.java) ?: return screenWidthPx
        return wm.maximumWindowMetrics.bounds.width().coerceAtLeast(1)
    }

    private fun resolveStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dpToPx(28f).toInt()
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
