# Anilib technical-debt audit

Audit date: 2026-08-20. This document evaluates maintainability and delivery
risk; it does not replace the product queue in `ROADMAP.md` or the visual parity
status in `UI_AUDIT.md`.

## Executive summary

The repository has a sound architectural spine: 77 explicitly owned modules,
inward dependencies, typed Bundles, platform SDK isolation, and a dependency-free
quality gate. The main debt is no longer the module graph. It is concentrated in
the shared Compose adapter, the external-extension compatibility boundary, and
the lack of packaged-host end-to-end evidence.

Current priorities are:

1. prove complete user journeys in packaged Android and desktop applications;
2. turn extension ABI failures into a measured compatibility matrix and stable
   user-facing capability states;
3. decompose the largest Compose routes without duplicating feature behavior;
4. make localization and UI contracts typed instead of relying on a growing
   text-rewrite catalogue;
5. establish performance budgets with profiler traces on real hosts.

## Measured baseline

| Measure | Result | Interpretation |
| --- | ---: | --- |
| Declared modules | 77 | The graph is deliberately fine-grained and remains guarded by `module.properties` |
| Java sources | 744 | Shared behavior and tooling remain Java-first |
| Kotlin sources | 57 | Kotlin is confined to platform adapters as intended |
| Source files | 801 | Includes production, architecture fixtures, and platform tests |
| Source lines | 77,015 | A large enough codebase that manual-only consistency checks are unsafe |
| Compose adapter lines | 15,303 | About one fifth of all source lines live in the shared UI adapter |
| Test or architecture-fixture files | 47 | Strong contract coverage exists, but packaged UI journeys remain the larger gap |
| Explicit unsupported-operation sites | 16 total, 12 production | Mostly intentional compatibility/capability boundaries; each still needs a stable UI state |
| Importable direct type qualifiers | 0 after this audit | Enforced by `direct-type-qualifier`; real simple-name collisions remain legal |

Largest current files:

| File | Lines | Main risk |
| --- | ---: | --- |
| `Platforms/Compose/.../AnilibApp.kt` | 2,531 | navigation, state orchestration, history, details, and dialogs change together |
| `Platforms/Compose/.../ExtensionRepositoriesScreen.kt` | 1,881 | catalogue, installation, trust, filters, and management are tightly coupled |
| `Platforms/Compose/.../DiscoveryScreen.kt` | 1,752 | sources, extensions, migration, search, and adaptive layout share one route |
| `Platforms/Compose/.../ReaderScreen.kt` | 1,367 | input, overlay, image loading, navigation, and preferences are difficult to isolate |
| `Tooling/ArchitectureTests/.../ExtensionRepositoryTest.java` | 1,289 | broad fixtures make failures slower to localize |
| `Features/Downloads/Runtime/.../DefaultDownloadService.java` | 1,276 | queue, persistence, policy, transfer, cleanup, and recovery evolve together |
| `Platforms/Compose/.../TrackingScreen.kt` | 1,111 | provider login, binding, edit, and synchronization presentation are coupled |
| `Platforms/Compose/.../SettingsScreen.kt` | 1,025 | unrelated settings destinations share a large rendering unit |

Line count is a signal, not an automatic defect. A split is useful only when it
creates an owned contract, an independently testable state machine, or a clear
failure boundary.

## Open debt register

### TD-01 — Packaged-host journeys are not yet the release oracle

- **Priority:** P0
- **Evidence:** `ROADMAP.md` still leaves packaged Android screenshot execution,
  joined Android/desktop journeys, accessibility passes, and profiler traces
  unchecked. Shared service fixtures cannot detect native browser, media,
  filesystem, lifecycle, focus, or installer regressions.
- **Risk:** a green shared gate can still ship a white screen, native crash,
  broken back stack, unavailable WebView, or player/reader failure.
- **Target:** one deterministic journey harness per packaged host using the same
  fixture repository: install source, browse, open details, play/read, resume,
  download, restart offline, backup, and restore.
- **Done when:** Android and desktop run those journeys in CI; failures retain a
  screenshot, structured diagnostic ID, platform log, and last stable route.
- **Owners:** `Platforms/Android`, `Platforms/Desktop`, `Platforms/Compose`, and
  `Tooling/ArchitectureTests` only for shared fixture construction.

### TD-02 — External extension compatibility is reactive rather than measured

- **Priority:** P0
- **Evidence:** the desktop host contains explicit unsupported paths for base
  source methods and lazy hoster videos. Compatibility work is spread across
  the host ABI, bytecode relocation, repository runtime, and Android bridge.
- **Risk:** an extension can install successfully but fail later on details,
  chapters, episodes, pages, or streams; generic exceptions do not explain the
  missing capability.
- **Target:** a versioned capability report produced before activation. It must
  identify implemented source operations, Kotlin/Android ABI requirements,
  filters/preferences, hosters, and required WebView behavior.
- **Done when:** a pinned representative anime and manga matrix passes on Android
  and all desktop targets; unsupported capabilities disable only their action
  and show a localized popup with the diagnostic ID and remediation.
- **Owners:** `Platforms/DesktopExtensionHost`, `Platforms/Android`, and
  `Features/ExtensionRepository`.

### TD-03 — Shared Compose has oversized route coordinators

- **Priority:** P1
- **Evidence:** the four largest Compose files total 7,531 lines. `AnilibApp.kt`
  owns unrelated route state and detail/history behavior; source and library
  title entry points have historically produced divergent detail flows.
- **Risk:** visual fixes regress navigation, back behavior, media ownership, or
  another platform because state lifetime is implicit in one large composable.
- **Target:** route coordinators consume immutable feature UI models and emit
  typed actions. Reusable title details, media rows, error/loading/empty states,
  and back-stack rules have one implementation.
- **Done when:** no route file exceeds an agreed budget without a documented
  exception; anime/manga and library/source entry paths render the same detail
  component; route-state tests cover back, tab switch, restore, and failure.
- **Owners:** `Platforms/Compose` plus the owning feature `Ui` module for each
  platform-neutral model. Feature behavior must not move into Compose.

### TD-04 — Extension repository presentation has too many responsibilities

- **Priority:** P1
- **Evidence:** `ExtensionRepositoriesScreen.kt` has 1,881 lines while the
  repository runtime also contains two files above 870 lines.
- **Risk:** refresh, install, uninstall, trust, artwork, source activation, and
  searching can leave different snapshots visible after one operation.
- **Target:** explicit state machines for repository synchronization, artifact
  installation, trust confirmation, and installed-source activation. Compose
  renders those states and never owns refresh sequencing.
- **Done when:** install/uninstall/update immediately produces one authoritative
  catalogue and source snapshot without manual refresh; concurrent and rollback
  tests cover every transition.
- **Owners:** `Features/ExtensionRepository/Runtime`, its `Ui` module, then the
  Compose adapter.

### TD-05 — Localization still depends on raw display text

- **Priority:** P1
- **Evidence:** `UiTranslations.kt` is 607 lines and translates many dynamic
  labels through prefixes and regular expressions. This is useful as a migration
  layer but fragile when English punctuation or wording changes.
- **Risk:** a harmless copy edit silently breaks French, and feature-owned text
  cannot be validated independently.
- **Target:** feature-owned translation keys with typed arguments and complete
  English/French resource sets. Compose receives resolved text or a typed text
  reference, never uses English as an identifier.
- **Done when:** the prefix/regex compatibility table is empty, every feature
  owns its keys, and JavaQuality rejects missing, duplicate, and unused keys.
- **Owners:** `Framework/Localization`, every feature `Ui`, and
  `Platforms/Compose` for final resolution.

### TD-06 — Test locality and failure diagnosis can improve

- **Priority:** P1
- **Evidence:** most shared contract tests are intentionally centralized in
  `Tooling/ArchitectureTests`; its extension repository fixture alone is 1,289
  lines. This protects the dependency policy but makes ownership less obvious.
- **Risk:** a broad suite failure is harder to attribute and discourages narrow
  execution before the full gate.
- **Target:** retain dependency-free architecture tests, but split fixtures and
  suites by feature workflow and publish a documented narrow Gradle task for
  each high-risk feature.
- **Done when:** extension, player, reader, download, backup, tracker, and update
  failures name one workflow; the owning narrow task runs before the full gate;
  packaged-host journeys remain separate from contract fixtures.
- **Owners:** `Tooling/ArchitectureTests` and the corresponding feature owner.

### TD-07 — Performance claims need host budgets

- **Priority:** P2
- **Evidence:** startup and 10,000-title persistence have gates, but memory,
  scrolling, reader cache, player relay, downloads, and extension-host budgets
  are still unchecked in `ROADMAP.md`.
- **Risk:** UI and media regressions appear as freezes or dropped frames without
  a reproducible threshold.
- **Target:** fixed datasets, devices, trace procedure, and budgets for frame
  time, startup, resident memory, image cache, relay throughput, and downloads.
- **Done when:** traces are retained for Android and desktop baselines and CI
  rejects statistically meaningful regressions.
- **Owners:** platform adapters and the relevant feature Runtime.

### TD-08 — Release acceptance is documented but not executed

- **Priority:** P2
- **Evidence:** production secrets, tagged multi-platform release, installation,
  and recorded acceptance remain unchecked.
- **Risk:** reproducible packaging can still fail at signing, notarization,
  update hand-off, store ingestion, or clean-machine startup.
- **Target:** an auditable release rehearsal on clean Android, Windows, Linux,
  and macOS hosts.
- **Done when:** every signed artifact installs, launches, updates, and completes
  the smoke journey; checksums, provenance, and acceptance evidence are retained.
- **Owners:** platform release modules and repository workflows.

## Debt retired in this audit

- [x] Playback progress on title episode rows now uses exact `mm:ss` or
  `h:mm:ss`, consistent with resume/history presentation instead of rounded
  whole minutes.
- [x] Shared media-position formatting has one Compose utility for episode,
  history, and resume labels.
- [x] `AnilibJava` rejects package-qualified types that should be imports in both
  Java and Kotlin.
- [x] The formatter migrated 103 existing source files; all importable direct
  qualifiers are removed.
- [x] Real simple-name collisions remain supported, so the rule does not force
  invalid or ambiguous imports.
- [x] Desktop extension-host Kotlin ABI imports are explicitly allowlisted only
  inside that platform boundary.

## Review cadence and completion rules

Update this audit when a P0/P1 item is retired, when a source file crosses a
documented size budget, or before a release candidate. Never close an item for
an API, mock, or placeholder alone. Closure requires reachable behavior,
persistence/restart where relevant, localized failure states, and verification
on every named platform.

The mandatory repository gates remain:

```powershell
.\gradlew.bat --no-daemon --console=plain javaQuality
.\gradlew.bat --no-daemon --console=plain architectureTest
.\gradlew.bat --no-daemon --console=plain check
```
