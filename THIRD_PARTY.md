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
| Compose WebView Multiplatform | 2.0.3 | Android System WebView and desktop KCEF browser surface | Apache-2.0 |

## Optional desktop compatibility runtime

| Component | Version | Purpose | License |
| --- | --- | --- | --- |
| Miwayomi | 0.2.9 | Isolated loopback sidecar for existing Aniyomi and Mihon APK sources | Apache-2.0 |

Miwayomi is not linked into Anilib or shipped inside its artifacts. The desktop
application downloads the pinned official release only after an explicit APK
installation action, enforces its published size and SHA-256 digest, and runs it
out of process.

## Anilib desktop extension engine

| Component | Version | Purpose | License |
| --- | --- | --- | --- |
| dex2jar | 2.4.38 | Convert extension DEX bytecode into JVM classes | Apache-2.0 |
| Apk Parser | 2.6.10 | Read APK manifests and extension entry points | Apache-2.0 |
| ASM | 9.10.1 | Repair and relocate converted extension bytecode | BSD-3-Clause |

These libraries are confined to the optional out-of-process DesktopExtensionHost.
They never enter the shared Kernel, Features, Android application, or portable
source ABI.

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
- <https://github.com/KevinnZou/compose-webview-multiplatform>
- <https://github.com/miwayomi/miwayomi>
- <https://android.googlesource.com/platform/tools/base>
- <https://android.googlesource.com/platform/frameworks/support>

Adding or upgrading an entry requires updating the exact allowlist in
`Anilib/Tooling/JavaQuality`, rerunning the complete gate, and recording the
change here.
