# Third-party platform UI inventory

Anilib's shared Java product core has no third-party runtime or test-library
dependency. The following dependencies are approved only for outer platform UI
adapters and are enforced by `AnilibJava`.

## Desktop Compose

| Component | Version | Purpose | License |
| --- | --- | --- | --- |
| Kotlin JVM and Compose compiler plugins | 2.4.10 | Compile the platform renderer | Apache-2.0 |
| Compose Multiplatform | 1.11.0 | Desktop runtime, Material 3, and icons | Apache-2.0 |

Primary projects:

- <https://github.com/JetBrains/kotlin>
- <https://github.com/JetBrains/compose-multiplatform>

Adding or upgrading an entry requires updating the exact allowlist in
`Anilib/Tooling/JavaQuality`, rerunning the complete gate, and recording the
change here.
