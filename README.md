# PanelSwipe

Extend the One UI **Notifications vs Quick Settings hot zone** to your home
screen.

On a Galaxy device you can split the status-bar swipe-down so that the **left**
side opens **Notifications** and the **right** side opens **Quick Settings**
(and QuickStar in Good Lock lets you move that divider). One UI's *“swipe down
on the home screen”* feature ignores this — it always opens Notifications no
matter where you swipe. PanelSwipe brings the hot zone to the home screen too.

## How it works

PanelSwipe is a single **Accessibility Service** with a small settings screen.
It uses the Android 14+ accessibility *motion-event observation* API
(`setMotionEventSources(SOURCE_TOUCHSCREEN)` + `onMotionEvent`) to **watch**
touch gestures **without consuming them**, then:

1. Acts only while a launcher (home screen) is in the foreground.
2. When you swipe **down**, it checks where the swipe **started** on the X axis.
3. Left of the configurable divider → **Notifications**
   (`GLOBAL_ACTION_NOTIFICATIONS`); right of it → **Quick Settings**
   (`GLOBAL_ACTION_QUICK_SETTINGS`).

Design goals, all met by this approach:

- **Whole screen** – it sees touches anywhere on the home screen.
- **Zero interference** – events are *observed only*, so app icons, taps,
  long-presses and page swipes keep working normally. There is **no overlay
  window**.
- **No battery drain** – no overlay, no foreground service, no polling, no wake
  locks. It only reacts to touch callbacks the framework already produces, and
  short-circuits immediately when you're not on the home screen.
- **Invisible** – there is nothing drawn on screen and no persistent
  notification.

> Note: the accessibility motion-event API requires **Android 14+**
> (`minSdk 34`). The Galaxy S25 Ultra on One UI 8.x is well above this.

## Build

You need Android Studio (or the Android SDK + this repo's Gradle wrapper).

```bash
# from the project root
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Then install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just open the project in Android Studio and press **Run**.

## Set up on the phone

1. Launch **PanelSwipe** and tap **Enable PanelSwipe** → turn the service on
   under *Settings ▸ Accessibility ▸ Installed apps ▸ PanelSwipe*.
2. Drag the **Hot-zone divider** to match the divider you set in QuickStar.
3. **Important:** In One UI *Settings ▸ Home screen*, turn **off**
   *“Swipe down for notification panel.”* Otherwise One UI always opens
   Notifications first and overrides PanelSwipe.
4. (Optional) Use **Swap sides** and **Swipe sensitivity** to taste.

The system status bar keeps its own hot zone; PanelSwipe only handles swipes
that start **below** the status bar, so the two don't fight.

## Privacy

PanelSwipe declares `canRetrieveWindowContent="false"` — it never reads the
contents of your screen. It only needs the package name of the foreground app
(to know when you're on the home screen) and raw touch coordinates of your
swipe. Nothing leaves the device; there is no network permission.

## Project layout

```
app/src/main/java/com/panelswipe/app/
  SwipeAccessibilityService.kt  # gesture detection + panel actions
  Prefs.kt                      # settings storage + permission check
  MainActivity.kt               # Compose settings screen
app/src/main/res/xml/accessibility_service_config.xml
```
