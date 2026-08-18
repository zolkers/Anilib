# Anilib product roadmap

## Vision

Anilib is a cross-platform anime and manga application for Android, Windows,
Linux, and macOS. Its visible product target is Aniyomi: the same recognizable
library, browse, reader, player, download, tracking, backup, WebView, and
settings workflows, adapted where a desktop layout benefits from more space.

The implementation is intentionally different. Shared behavior is Java 21,
dependency-free, and organized as removable vertical features. A small Kernel
starts an explicit graph of Bundles. Compose Multiplatform is allowed only in
the outer UI adapters. Android SDK and desktop-native types never enter shared
features. `AnilibJava` protects those boundaries and the release contract.

The source model is bring-your-own-supply. Anilib ships no third-party
catalogue. Users may add HTTPS indexes or GitHub repositories with arbitrary
printable package identities. Signed Anilib source Bundles are the executable
cross-platform format. Existing Aniyomi APKs remain an isolated, best-effort
Android compatibility path; Anilib will not recreate `eu.kanade.*` host classes
or import Aniyomi's dependency graph into the portable architecture.

## Documentation map

- [`ARCHITECTURE.md`](ARCHITECTURE.md) defines non-negotiable module and runtime
  boundaries.
- [`PARITY.md`](PARITY.md) records what the product can do today and the gap for
  each major area.
- [`UI_AUDIT.md`](UI_AUDIT.md) preserves the screen-by-screen comparison against
  the pinned Aniyomi reference revision.
- This file is the only forward-looking work queue. A checked item is delivered;
  an unchecked item is still product work.

## Start here with no project context

### Required toolchain

- Git and a Java 21 JDK are required for every task.
- Android work requires Android SDK platform 37 and a configured `ANDROID_HOME`.
- Desktop UI and packaging use Compose Multiplatform; native MSI, DEB, and DMG
  packages must be built on Windows, Linux, and macOS respectively.
- From Windows, use `gradlew.bat`; from Linux or macOS, use `./gradlew`.
- Do not install a library merely to make one task easier. Check the dependency
  policy first and prefer JDK APIs or an existing Anilib contract.

Before changing a module, read the root [`AGENTS.md`](../AGENTS.md),
[`ARCHITECTURE.md`](ARCHITECTURE.md), that module's `module.properties`, its
build file, and the nearest tests. Existing uncommitted work belongs to the user
and must not be overwritten.

### Repository map

```text
Anilib/
  Foundation/       dependency-free shared values and validation
  Framework/        reusable HTTP, backup, storage, and media contracts
  Kernel/           Bundle graph, lifecycle, capabilities, contributions
  Features/         vertical product capabilities
  Configurations/   explicit product assembly; Standard is the main product
  Platforms/        shared Compose UI plus Android and desktop adapters
  Examples/         official portable source template
  Tooling/          AnilibJava, architecture tests, source publisher
```

A normal feature may contain `Api`, `Runtime`, `Ui`, and `Bundle` modules. Only
create the layers it actually needs. The Bundle is the sole selectable unit;
do not add classpath scanning, a second registry, or an implicit global service
locator. `Configurations/Standard` selects Bundles. Platforms translate native
lifecycle, UI, browser, filesystem-picker, notification, media, and installer
operations, but they do not duplicate feature behavior.

### Non-negotiable engineering rules

- Shared Java packages start with `fr.vriege.anilib` and target Java 21.
- Foundation, Framework, Kernel, Features, Configurations, Tooling, and tests
  have no third-party runtime dependencies.
- Kotlin and external UI/media libraries are restricted to allowlisted Platform
  adapters. Android or desktop SDK types stay in their matching adapter.
- Dependencies point inward: Platform, Configuration, Feature, Kernel,
  Framework/Foundation. Production code never depends on Tooling.
- Public collaboration between features uses an API, typed capability, or typed
  contribution point. State remains owned by the feature that writes it.
- Do not add Javadocs or KDocs. `AnilibJava` rejects them repository-wide.
- Do not add `eu.kanade.*` compatibility classes. Aniyomi APK support is an
  optional Android boundary; portable signed Anilib Bundles are the source ABI.
- Source code never ships or hardcodes a third-party catalogue URL. Repository
  URLs are user-managed, HTTPS-only inputs.
- Use small Conventional Commits and stage explicit paths. A completed roadmap
  item must include its tests, documentation update, and commit.

### How to deliver one roadmap item

1. Pick the first unchecked item whose prerequisites are already delivered.
2. Locate the owning feature. Extend its public Java contract first only when
   the behavior truly needs a new cross-module API.
3. Implement shared behavior in that feature's Runtime/Core and expose a
   platform-neutral presentation from its Ui module.
4. Publish the capabilities from its single Bundle and select that Bundle only
   in the product configuration that needs it.
5. Add the minimum Android/desktop adapter code required to render or invoke the
   same shared behavior. A platform-specific exception must be named in the
   roadmap and parity matrix.
6. Add focused architecture tests for success, invalid input, persistence,
   restart, rollback, bounds, and trust/security behavior as applicable.
7. Run the narrow owning task, then all three repository gates:

   ```powershell
   .\gradlew.bat --no-daemon --console=plain javaQuality
   .\gradlew.bat --no-daemon --console=plain architectureTest
   .\gradlew.bat --no-daemon --console=plain check
   ```

8. Update the matching `PARITY.md` and `UI_AUDIT.md` rows, check only behavior
   that is reachable and verified, then make a small Conventional Commit.

An item is not complete because an API or visual placeholder exists. Its normal
flow, empty/loading/error states, persistence and restart behavior, both shared
applications, and relevant security limits must work. A release item additionally
requires installation or verification on the target operating system.

### External source model

An index JSON is a dynamic catalogue, not scraping code. Anilib accepts the
Aniyomi-style index fields used by common user-supplied repositories and keeps
arbitrary printable `pkg` values as opaque identities. Each executable
cross-platform entry points to a bounded Java JAR built against the Anilib Source
SDK, plus SHA-256, Ed25519 signature, publisher identity, API range, module name,
factories, and declared permissions. The host verifies all of them before the
next restart-isolated module-layer load.

Aniyomi repositories usually point to Android APKs. Android can download them,
ask the system to install them, inspect metadata/certificates, and attempt the
isolated bridge only when its host-ABI preflight passes. Desktop cannot execute
an Android APK. To support both products, publish an Anilib Bundle; the official
template and publisher under `Examples/SourceTemplate` and `Tooling/SourcePublisher`
produce the JAR, signatures, `index.json`, and `index.min.json` without an
external runtime library.

### Recommended next task

Start with Library display modes and category CRUD. They affect the main daily
screen and provide reusable selection/category primitives needed by bulk
downloads, migration, update filters, and richer statistics. Keep those changes
inside Library until another feature needs a narrow public capability.

## Delivered foundation

- [x] Java 21 modular core, typed capabilities, contributions, transactional
  lifecycle, explicit Bundles, and dependency-free architecture tests
- [x] shared adaptive Compose shell for Android and desktop with Library,
  Updates, History, Browse, and More navigation
- [x] durable library, categories, favourites, history, progress, local folders,
  CBZ reading, cover cache, discovery, migration, Reader, Player, Downloads,
  Tracking SDK, updates, notifications, backup, and Aniyomi backup import
- [x] shared HTTP policy with cookies, cache, rate limits, restricted extension
  clients, Android System WebView, desktop KCEF, and browser-session hand-off
- [x] signed portable source SDK, template, publisher, SHA-256 and Ed25519
  verification, deterministic indexes, example repository, GitHub publication,
  installation, enable/disable/remove, and manual/automatic update channels
- [x] arbitrary printable `pkg` identities and dynamic GitHub index discovery on
  Android and desktop
- [x] durable shared Settings with theme, start screen, adult-content, incognito,
  Wi-Fi policies, dedicated destinations, and confirmed maintenance actions
- [x] metadata, trust, ABI preflight, PackageInstaller hand-off, and isolated
  best-effort anime/manga adapters for visible Android extension APKs
- [x] reproducible MSI, DEB, DMG, and APK packaging; production Android,
  Authenticode, and Apple signing; notarization; checksums; provenance
  attestations; atomic GitHub Releases; and a shared stable update check
- [x] repository-wide no-Javadoc/KDoc rule, conventional commits, UI audit, and
  full Android/desktop verification gate

## Remaining product work

The ordering below is intentional: finish daily workflows before optional
polish, then harden distribution. Every shared behavior must land in its owning
feature Bundle and be reachable from both applications unless marked
platform-specific.

### 1. Library, details, history, and statistics

- [x] add persisted grid/list modes, density, sorting, and configurable default
  category or landing behavior
- [x] add multi-selection and bulk category, favourite, download, migrate, and
  delete actions
- [x] add category create, rename, reorder, delete, per-category display, and
  update policies
- [x] add complete anime/manga detail units, artwork, metadata editing, related
  titles, share, open-in-WebView, and source actions
- [x] add history date grouping, resume/remove actions, and distinct anime/manga
  presentation
- [x] expand statistics by status, source, language, score, duration, progress,
  and activity period

### 2. Browse, sources, extensions, and migration

- [x] add source and extension update badges, richer overflow actions, and exact
  Aniyomi density and loading/error states
- [x] add a dedicated extension detail route with permissions, trust identity,
  versions, changelog, install progress, retry, and failure diagnostics
- [x] persist catalogue grid/list choice and add catalogue item menus
- [x] add batch migration, seasonal-anime handling, source comparison, options,
  preview, progress, cancellation, and partial-failure recovery
- [x] run public compatibility fixtures for Anilib repository indexes that use
  the Yuzono and Keiyoushi JSON shapes without redistributing their catalogues

### 3. Reader

- [x] add complete gesture and tap-zone customization
- [x] add scaling, fit policy, crop borders, split pages, rotation, dual-page,
  and webtoon spacing controls
- [x] add color filters, brightness, transitions, orientation policy, and
  per-title overrides
- [x] add chapter lists, next/previous chapter navigation, reader menus, and
  download/read-state actions at Aniyomi depth

### 4. Player

- [x] complete transport controls, gestures, seeking, speed, orientation,
  brightness, volume, lock, and custom-button behavior
- [x] add decoder, audio, subtitle, quality, skip-intro/outro, and per-title
  preference policies
- [x] add Android picture-in-picture and background/audio behavior
- [x] add desktop advanced controls and mpv-compatible policy where the selected
  native backend supports it

### 5. Downloads and library updates

- [x] add queue grouping, reorder, priority, delete-all, speed, ETA, and richer
  partial/error recovery
- [x] add user-selectable storage locations, validation, migration, index repair,
  and per-title download management
- [x] complete automatic-download rules, category rules, episode/chapter limits,
  and cleanup policies
- [x] add update date grouping, selection actions, skipped-reason views,
  per-item download actions, exceptions, and full schedule controls

### 6. Tracking and backup

- [x] ship opt-in first-party tracker Bundles with complete authentication flows
  for the selected services
- [x] add provider icons, richer search/edit dialogs, automatic synchronization,
  conflict handling, and sync preferences
- [x] add automatic backup schedules, retention, content selection, configurable
  destinations, SAF/native folder integration, and sharing/export

### 7. Settings, network, WebView, and diagnostics

- [x] add language packs, theme families, color schemes, typography, navigation
  style, and remaining category defaults and exceptions
- [x] add configurable user agent, proxy, DNS-over-HTTPS, timeout, cache, and
  per-source network diagnostics
- [x] add storage inspection, logs, crash reports, exportable diagnostics, and
  safe reset flows
- [x] add WebView file chooser, pop-ups, downloads, browser settings, and
  automatic challenge retry without provider-specific code in shared modules

### 8. Local source

- [x] implement the complete local manga folder conventions, metadata, covers,
  chapter ordering, and rescan actions
- [x] implement local anime metadata, episode JSON, video discovery, thumbnails,
  subtitles, and rescan actions

### 9. Release and application updates

- [x] add signed in-application artifact download with checksum and provenance
  verification
- [x] add platform-owned installation hand-off: PackageInstaller on Android,
  installer launch on Windows/Linux, and notarized DMG hand-off on macOS
- [x] decide and implement stable/beta channel policy plus changelog and licence
  presentation
- [ ] configure real production secrets, execute a tagged release on all four
  targets, install every artifact, and record the release acceptance checklist
- [x] prepare store metadata and optional Play Store, Microsoft Store, and macOS
  distribution paths without making stores mandatory

### 10. Product hardening

- [ ] add deterministic screenshot and interaction tests for every audited route
  on compact and expanded layouts
- [ ] add end-to-end tests for repository install, browse, read/play, download,
  backup/restore, update, and offline restart on Android and desktop
- [ ] complete accessibility semantics, keyboard navigation, focus, screen-reader,
  contrast, reduced-motion, and large-text audits
- [ ] measure startup, memory, scrolling, reader cache, player relay, download, and
  large-library performance; set regression budgets
- [ ] perform a security review of extension trust, archive parsing, WebView,
  loopback media relay, backup import, updater, and release supply chain
- [ ] complete user-facing localization and legal/licence notices

## Definition of product parity

Anilib reaches the target when all remaining items above are checked, every row
in `PARITY.md` is `Complete` or explicitly platform-specific, the pinned UI
audit has no untriaged workflow, the full quality gate is green, and signed
release artifacts have been installed and smoke-tested on Android, Windows,
Linux, and macOS. Architectural similarity alone never counts as product parity.
