# Anilib

Inspired project from aniyomi

## Commands

Use Java 21 from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain check
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Desktop:run
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Android:assembleDebug
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Android:writeAndroidReleaseChecksums
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Desktop:writeDesktopReleaseChecksums
.\gradlew.bat --no-daemon --console=plain javaQuality
```

## Legal

Anilib is an independent project and is not affiliated with Aniyomi, Mihon, or
their contributors. No Aniyomi source code is included in this bootstrap.
Aniyomi is acknowledged in [NOTICE](NOTICE) as product inspiration.

Copyright 2026 Victor Riegert. Licensed under Apache-2.0.
