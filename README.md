# Anilib

Anilib is a Java-first, cross-platform library, reader, and player application.
It targets functional parity with the broad product surface of
[Aniyomi](https://github.com/aniyomiorg/aniyomi), while using an independently
designed, Ghidra-inspired modular architecture.

Aniyomi is the product and UI reference: Anilib aims to preserve its navigation,
screens, actions, settings, and behavior closely. The independent refactor is
behind that experience, where modules and platform boundaries are redesigned
for Java-first Android and desktop support.

The project starts with four hard constraints:

- Java 21 for all neutral code;
- no third-party runtime or test libraries inside the shared product core;
- feature-owned vertical modules with explicit manifests;
- Android and desktop as outer platform adapters over the same product core.

The current bootstrap is intentionally a working architectural slice, not a
claim of complete Aniyomi feature parity. It includes a transactional plugin
kernel, a Library feature, a Compose Multiplatform desktop application, an
adaptive Compose UI shared with a real Android application, architecture tests,
the versioned Source extension SDK, a cross-platform JDK-only HTTP framework,
and an Aniyomi-style Browse slice with source listings, global and per-source
search, filters, persisted preferences, library admission, and migration. The
same Discovery behavior and adaptive Compose screen run on Android and desktop.
It also includes a removable Reader Bundle with local folder/CBZ pages,
persistent resume and history, bounded asynchronous prefetch, LTR, RTL,
vertical, and webtoon navigation, plus one immersive shared reader screen.
The removable Downloads Bundle adds a durable queue, bounded concurrent jobs,
pause/cancel/resume controls, restart-safe partial downloads, storage quotas,
and offline reading through the same Reader on Android and desktop.
The removable Backup Bundle creates checksum-verified local archives from
feature-owned versioned codecs, previews their contents, and restores Library
source preferences, playback state, and tracking bindings with merge semantics
and cross-section rollback.
The removable Player Bundle consumes Source API 1.4 streaming extensions,
exposes episodes, qualities, formats, request metadata, and subtitles, and
persists per-episode resume and watched state across Android and desktop. Its
narrow Java backend capability drives an Aniyomi-style shared video surface,
using Media3 on Android and native media engines on desktop without leaking
either implementation into the shared product core. Protected streams retain
their source-provided `Referer`, cookies, user agent, byte ranges, redirects,
subtitle headers, and HLS headers across playlists, segments, and encryption
keys.
The removable Tracker Bundle adds a versioned adapter SDK, explicit opt-in
registrations, restricted network contexts, account login/logout, remote title
search and binding, status, progress, score, dates, privacy, refresh, removal,
restart-safe mirrors, and shared Android/desktop tracking screens.
The removable Updates Bundle adds one non-overlapping background library job,
five source lanes, favourite/status/category filters, durable chapter and
episode baselines, an unread Updates feed, and a feature-owned backup section.
Desktop delivers native tray notices; Android uses dedicated notification
channels and an SDK alarm that resumes due work after process death. Both
platforms render the same Aniyomi-style progress, schedule, filter, failure, and
new-content screen.
The removable Extension Repository Bundle accepts only user-entered HTTPS index
URLs, parses the Aniyomi repository shape with strict resource limits, and
shows the resulting APK and portable Anilib artifacts in the shared Browse
experience. User-trusted Ed25519 publisher keys protect portable downloads,
whose checksum, signature, Source API compatibility, and internal descriptor
are verified before durable install, update, disable, or removal. Enabled
portable Bundles are revalidated on restart and loaded through one explicit,
in-memory JPMS layer per artifact into the shared Source registry; a broken
artifact is reported without blocking valid Bundles. Anilib ships with no
third-party source catalogue. A pasted GitHub repository URL is resolved through
its default branch and conventional `repo` publication branch, with dynamic
`index.min.json` then `index.json` discovery; the same signed portable Bundle is
installed by Android and desktop.
On Android, the same screen inventories installed Aniyomi extension APKs that
the OS already exposes, including their source entrypoints, Aniyomi library
generation,
content flags, and signing-certificate fingerprints. This metadata-only bridge
does not load APK extension bytecode or request unrestricted package visibility;
ABI-ready anime APKs now enter a pre-start adapter that turns catalogue pages,
episodes, streams, headers, and subtitles into ordinary Anilib Source Bundles;
activation failures stay isolated to their package. Supplying the complete
Aniyomi host ABI is still required before current installed APKs can execute.
Portable Anilib Bundles remain the executable cross-platform format.
Before that future bridge may activate anything, Android requires explicit
package-certificate trust and performs a non-initializing audit of the required
host-ABI groups. A signing-certificate change invalidates the stored decision.
The removable Settings Bundle atomically persists shared appearance and policy
preferences. System, light, and dark themes apply live on Android and desktop;
the shared Aniyomi-style hierarchy also provides confirmed actions for clearing
the common HTTP cookie jar, embedded-browser cookies, and response cache. Source
API 1.6 optionally exposes source and title web pages through the shared Browse
surface; Android renders them with System WebView and desktop with KCEF while
both exchange session cookies with the platform-neutral HTTP jar. Sources can
carry their request headers and User-Agent into that browser and declare the
cookies that prove a web challenge is complete. Policy enforcement and deeper
rows remain tracked explicitly in the parity matrix. A separate confirmed
action clears Android WebView cache and site storage immediately; desktop
schedules the locked KCEF profile for removal before the next engine startup.
The repository also contains the dependency-free
`AnilibJava` quality checker. Kotlin and audited UI dependencies are confined
to outer platform renderers; shared contracts and behavior remain Java 21.

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

Only platform UI modules configure dependency repositories. The current
allowlist contains Kotlin, Compose Multiplatform, ComposeMediaPlayer, Android
Gradle Plugin, and the exact AndroidX components used by their owning platform
builds. The Android SDK remains an outer platform toolchain and may not leak
into neutral modules.
See [THIRD_PARTY.md](THIRD_PARTY.md) for the exact audited coordinates.

See [ARCHITECTURE.md](Anilib/ARCHITECTURE.md) and
[ROADMAP.md](Anilib/ROADMAP.md) for the dependency contract and parity plan.
Desktop packaging and the three-host release matrix are documented in
[Desktop release](Anilib/Platforms/Desktop/README.md).
Android APK packaging, optional signing, and release secrets are documented in
[Android release](Anilib/Platforms/Android/README.md).

## Legal

Anilib is an independent project and is not affiliated with Aniyomi, Mihon, or
their contributors. No Aniyomi source code is included in this bootstrap.
Aniyomi is acknowledged in [NOTICE](NOTICE) as product inspiration.

Copyright 2026 Victor Riegert. Licensed under Apache-2.0.
