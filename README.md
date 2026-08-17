# Anilib

Anilib is a Java-first, cross-platform library, reader, and player application.
It targets functional parity with the broad product surface of
[Aniyomi](https://github.com/aniyomiorg/aniyomi), while using an independently
designed, Ghidra-inspired modular architecture.

The project starts with four hard constraints:

- Java 21 for all neutral code;
- no third-party runtime or test libraries;
- feature-owned vertical modules with explicit manifests;
- Android and desktop as outer platform adapters over the same product core.

The current bootstrap is intentionally a working architectural slice, not a
claim of complete Aniyomi feature parity. It includes a transactional plugin
kernel, a Library feature, a runnable Swing desktop shell, an Android-neutral
host seam, architecture tests, and the dependency-free `AnilibJava` quality
checker.

## Commands

Use Java 21 from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain check
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Desktop:run
.\gradlew.bat --no-daemon --console=plain javaQuality
```

No dependency repository is configured because the build has no external
library dependency. The Android SDK will eventually be the platform toolchain
for producing an APK; it is not allowed to leak into neutral modules.

See [ARCHITECTURE.md](Anilib/ARCHITECTURE.md) and
[ROADMAP.md](Anilib/ROADMAP.md) for the dependency contract and parity plan.

## Legal

Anilib is an independent project and is not affiliated with Aniyomi, Mihon, or
their contributors. No Aniyomi source code is included in this bootstrap.
Aniyomi is acknowledged in [NOTICE](NOTICE) as product inspiration.

Copyright 2026 Victor Riegert. Licensed under Apache-2.0.
