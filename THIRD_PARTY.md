# Third-party platform UI inventory

Anilib's shared Java product core has no third-party runtime or test-library
dependency. The following dependencies are approved only for outer platform UI
adapters and are enforced by `AnilibJava`.

## Desktop Compose

| Component                                               | Version | Purpose                                                    | License    |
|---------------------------------------------------------|---------|------------------------------------------------------------|------------|
| Kotlin JVM and Compose compiler plugins | 2.4.10  | Compile the Desktop renderer                    | Apache-2.0 |
| Compose Multiplatform                   | 1.11.1  | Desktop runtime, Material 3, and icons           | Apache-2.0 |
| ComposeMediaPlayer                      | 0.11.4  | Desktop video surface over native media engines  | Apache-2.0 |
| Compose WebView Multiplatform           | 2.0.3   | Desktop KCEF browser surface                     | Apache-2.0 |
| FFmpeg                                   | 9.0.1   | Finalize resumable video downloads as local MP4  | LGPL-2.1+  |

## Optional desktop compatibility runtime

| Component | Version | Purpose                                                              | License    |
|-----------|---------|----------------------------------------------------------------------|------------|
| Miwayomi  | 0.2.9   | Isolated loopback sidecar for existing Aniyomi and Mihon APK sources | Apache-2.0 |

Miwayomi is not linked into Anilib or shipped inside its artifacts. The desktop
application downloads the pinned official release only after an explicit APK
installation action, enforces its published size and SHA-256 digest, and runs it
out of process.

## Anilib desktop extension engine

| Component                  | Version | Purpose                                              | License      |
|----------------------------|---------|------------------------------------------------------|--------------|
| dex2jar                    | 2.4.38  | Convert extension DEX bytecode into JVM classes      | Apache-2.0   |
| Apk Parser                 | 2.6.10  | Read APK manifests and extension entry points        | Apache-2.0   |
| ASM                        | 9.10.1  | Repair and relocate converted extension bytecode     | BSD-3-Clause |
| Kotlinx Serialization JSON | 1.9.0   | Match the metadata ABI used by source extensions     | Apache-2.0   |
| OkHttp                     | 5.4.0   | Provide the network ABI used by source extensions    | Apache-2.0   |
| RxJava                     | 1.3.8   | Provide the observable ABI used by source extensions | Apache-2.0   |
| jsoup                      | 1.19.1  | Parse source catalog and detail HTML                 | MIT          |

These libraries are confined to the optional out-of-process DesktopExtensionHost.
They never enter the shared Kernel, Features, Desktop application, or portable
source ABI.

Primary projects:

- <https://github.com/JetBrains/kotlin>
- <https://github.com/JetBrains/compose-multiplatform>
- <https://github.com/kdroidFilter/ComposeMediaPlayer>
- <https://github.com/KevinnZou/compose-webview-multiplatform>
- <https://ffmpeg.org/>
- <https://github.com/BtbN/FFmpeg-Builds>
- <https://github.com/miwayomi/miwayomi>

Adding or upgrading an entry requires updating the exact allowlist in
`Anilib/Tooling/JavaQuality`, rerunning the complete gate, and recording the
change here.
