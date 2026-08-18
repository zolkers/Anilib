# Third-party platform UI inventory

Anilib's shared Java product core has no third-party runtime or test-library
dependency. The following dependencies are approved only for outer platform UI
adapters and are enforced by `AnilibJava`.

## Shared and Desktop Compose

| Component | Version | Purpose | License |
| --- | --- | --- | --- |
| Kotlin JVM, Multiplatform, and Compose compiler plugins | 2.4.10 | Compile platform renderers | Apache-2.0 |
| Compose Multiplatform | 1.11.1 | Shared adaptive UI, Desktop runtime, Material 3, and icons | Apache-2.0 |
| ComposeMediaPlayer | 0.11.4 | Shared video surface over platform-native media engines | Apache-2.0 |

## Android application

| Component | Version | Purpose | License |
| --- | --- | --- | --- |
| Android Gradle Plugin | 9.1.1 | Build the Android library target and APK | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | Own the Android activity and Compose content boundary | Apache-2.0 |
| AndroidX Media3 HLS | 1.10.1 | HLS playback for the Android media backend | Apache-2.0 |

Primary projects:

- <https://github.com/JetBrains/kotlin>
- <https://github.com/JetBrains/compose-multiplatform>
- <https://github.com/kdroidFilter/ComposeMediaPlayer>
- <https://android.googlesource.com/platform/tools/base>
- <https://android.googlesource.com/platform/frameworks/support>

Adding or upgrading an entry requires updating the exact allowlist in
`Anilib/Tooling/JavaQuality`, rerunning the complete gate, and recording the
change here.
