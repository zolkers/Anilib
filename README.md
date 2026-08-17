# Anilib

Anilib is a Java-first, cross-platform library, reader, and player application.
It targets functional parity with the broad product surface of
[Aniyomi](https://github.com/aniyomiorg/aniyomi), while using an independently
designed, Ghidra-inspired modular architecture.

The project starts with four hard constraints:

- Java 21 for all neutral code;
- no third-party runtime or test libraries inside the shared product core;
- feature-owned vertical modules with explicit manifests;
- Android and desktop as outer platform adapters over the same product core.

The current bootstrap is intentionally a working architectural slice, not a
claim of complete Aniyomi feature parity. It includes a transactional plugin
kernel, a Library feature, a Compose Multiplatform desktop application, an
Android-neutral host seam, architecture tests, and the dependency-free
`AnilibJava` quality checker. Kotlin and audited UI dependencies are confined
to outer platform renderers; shared contracts and behavior remain Java 21.

## Commands

Use Java 21 from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain check
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Desktop:run
.\gradlew.bat --no-daemon --console=plain javaQuality
```

Only platform UI modules configure dependency repositories. The current
allowlist contains Kotlin and Compose Multiplatform for Desktop; the Android SDK
will remain an outer platform toolchain and may not leak into neutral modules.
See [THIRD_PARTY.md](THIRD_PARTY.md) for the exact audited coordinates.

See [ARCHITECTURE.md](Anilib/ARCHITECTURE.md) and
[ROADMAP.md](Anilib/ROADMAP.md) for the dependency contract and parity plan.

## Legal

Anilib is an independent project and is not affiliated with Aniyomi, Mihon, or
their contributors. No Aniyomi source code is included in this bootstrap.
Aniyomi is acknowledged in [NOTICE](NOTICE) as product inspiration.

Copyright 2026 Victor Riegert. Licensed under Apache-2.0.
