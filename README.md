# AppKiller — Android TV App Manager

A utility app for Android TV that lets you bulk force-stop recently used apps directly from your TV remote.

## Why

Android TV has no built-in way to quickly close multiple background apps. Force-stopping requires navigating to Settings → App Info → Force Stop for each app individually — tedious with a D-pad. AppKiller automates that flow.

## How it works

Force-stopping apps requires a privileged system permission (`FORCE_STOP_PACKAGES`) that is unavailable to third-party apps. AppKiller works around this by using an **AccessibilityService** to automate the system Settings UI: it navigates to each app's info page, clicks the Force Stop button, and confirms the dialog automatically.

## Features

- Lists apps used in the last 24 hours
- Kill all apps or select specific ones
- Protect apps from being killed (whitelist)
- Persists the whitelist across sessions
- Full D-pad / remote navigation support

## Requirements

- Android TV with API level 26+ (Android 8.0)
- Two permissions granted manually:
  1. **Usage Access** — to query recently used apps
  2. **Accessibility Service** — to automate the force-stop flow

The app walks you through granting both on first launch.

## Build

Java 21 is required (Java 25+ is incompatible with Gradle 8.4).

```bash
# Debug APK
ANDROID_HOME=/home/nix/Android/Sdk JAVA_HOME=/home/nix/.sdkman/candidates/java/21.0.10-tem ./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Localization

The accessibility service matches Force Stop button text in English and Spanish. To add another locale, add the translated strings in `ForceStopAccessibilityService.kt`.

| Locale  | Force Stop button  | Confirm  | Cancel    |
|---------|--------------------|----------|-----------|
| English | Force stop         | OK       | Cancel    |
| Spanish | Forzar detención   | Aceptar  | Cancelar  |
