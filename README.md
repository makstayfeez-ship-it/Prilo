# Project Optimizer

Android utility app built with Kotlin and Jetpack Compose.

## Current prototype (0.1.0)
- Dark interface with green accents.
- Five sections: Dashboard, Cleaning, Games, Monitoring, Settings.
- Reads available storage, available RAM, and Android thermal status where supported.
- GitHub Actions workflow builds a debug APK.

## Build
Requires JDK 17 and Gradle 8.9.

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Android limitations
This app does not claim to clear other apps' private caches, kill background processes, overclock hardware, or guarantee higher FPS. Those actions are restricted by Android for ordinary non-root apps. File deletion will be implemented through Android's user-approved system document picker.

## Next milestones
1. User-approved file analysis and deletion.
2. Installed-game list and game profiles.
3. Periodic maintenance with WorkManager.
4. Improved live monitoring and device compatibility.
