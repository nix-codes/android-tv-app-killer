# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
ANDROID_HOME=/home/nix/Android/Sdk JAVA_HOME=/home/nix/.sdkman/candidates/java/21.0.10-tem ./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clean build
ANDROID_HOME=/home/nix/Android/Sdk JAVA_HOME=/home/nix/.sdkman/candidates/java/21.0.10-tem ./gradlew clean assembleDebug
```

Java 21 is required — Java 25 (system default) is incompatible with Gradle 8.4. Always specify `JAVA_HOME` explicitly as shown above.

## Architecture

The app is an Android TV utility that lists recently-used apps (via `UsageStatsManager`) and force-stops them by automating the system Settings UI via an `AccessibilityService`.

### Why this approach

Force-stopping apps requires `FORCE_STOP_PACKAGES`, a privileged permission unavailable to third-party apps. The workaround: an `AccessibilityService` navigates to *Settings → App Info → Forzar detención* for each target app and clicks through the confirmation dialog automatically.

### Component responsibilities

- **`RunningAppsHelper`** — Queries `UsageStatsManager` for apps used in the last 7 days, filters out system apps, and maintains a `killedAt: Map<String, Long>` timestamp map. Apps are re-shown after refresh if their `lastTimeUsed` exceeds the kill timestamp (i.e. they were relaunched).

- **`ForceStopAccessibilityService`** — State machine (`IDLE → WAITING_FOR_FORCE_STOP_BTN → WAITING_FOR_CONFIRM_BTN`). Processes a queue of package names one at a time: opens the Settings app info page, waits for accessibility events to confirm the page has loaded, clicks the Force Stop button, then confirms the dialog. Skips a package if the button is absent after 3 events on the settings window (app not running) or after a 40-event global timeout.

- **`MainActivity`** — Enforces a two-step permission gate on `onResume`: Usage Access first, then Accessibility Service. Loads the app list on a background thread.

### Permissions required at runtime

Both must be granted manually by the user (not runtime-requestable):
1. **Usage Access** — `Settings.ACTION_USAGE_ACCESS_SETTINGS` — needed by `RunningAppsHelper`
2. **Accessibility Service** — `Settings.ACTION_ACCESSIBILITY_SETTINGS` — needed by `ForceStopAccessibilityService`

### Localization note

The accessibility service searches for Force Stop button text in both English and Spanish. The confirmed Spanish strings for this device are: `"Forzar detención"` (button), `"Aceptar"` (confirm), `"Cancelar"` (cancel). Add strings here if supporting other locales.

### TV-specific notes

- The launcher category is `LEANBACK_LAUNCHER` — the app appears in the Android TV home screen
- All interactive views must be `focusable="true"` for D-pad navigation; `setOnClickListener` works via DPAD_CENTER
- `android.hardware.touchscreen` is declared as `required="false"`
