# Dependency Versions

All versions verified from official sources as of December 23, 2025.

## Build Tools

| Dependency | Version | Source |
|------------|---------|--------|
| AGP | 8.13.2 | developer.android.com/build/releases/gradle-plugin |
| Gradle | 8.13 | AGP 8.13 compatibility table |
| Kotlin | 2.3.0 | kotlinlang.org/docs/releases.html |
| JDK | 21 | Required for AGP 8.7+ |

## Android SDK

| Component | Version |
|-----------|---------|
| compileSdk | 35 (Android 15) |
| targetSdk | 35 (Android 15) |
| minSdk | 29 (Android 10) |

## Libraries

| Library | Version | Source |
|---------|---------|--------|
| Compose BOM | 2025.12.00 | developer.android.com/develop/ui/compose/bom |
| Hilt | 2.54 | developer.android.com/training/dependency-injection/hilt-android |
| Room | 2.7.1 | developer.android.com/jetpack/androidx/releases/room |
| OkHttp | 4.12.0 | square.github.io/okhttp |
| DataStore | 1.1.2 | developer.android.com/jetpack/androidx/releases/datastore |
| WorkManager | 2.10.0 | developer.android.com/jetpack/androidx/releases/work |
| CameraX | 1.5.2 | developer.android.com/jetpack/androidx/releases/camera |
| ML Kit Barcode | 17.3.0 | developers.google.com/ml-kit |
| Timber | 5.0.1 | github.com/JakeWharton/timber |
| Coil | 2.7.0 | coil-kt.github.io/coil |
| Kotlinx Serialization | 1.7.3 | github.com/Kotlin/kotlinx.serialization |

## Version Update Policy

- Always verify versions from official sources before updating
- Test thoroughly after dependency updates
- Check compatibility matrices for breaking changes
- Maintain this file when updating dependencies

