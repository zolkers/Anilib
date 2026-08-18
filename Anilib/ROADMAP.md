# Functional Parity Roadmap

The target is broad product parity with Aniyomi, delivered as removable vertical
features rather than a port of its internal Kotlin/Android layering.

Product parity means matching Aniyomi's visible information architecture,
navigation, screen hierarchy, labels, actions, states, settings, and behavior as
closely as each platform permits. Anilib may adapt window layout for desktop,
but it must not simplify or reinterpret an Aniyomi workflow merely because its
internal implementation is different. The refactor applies to ownership and
modularity behind the UI; Aniyomi remains the product reference.

This roadmap records architectural delivery milestones. It is not a percentage
of end-user parity. The auditable screen and behavior comparison lives in
[`PARITY.md`](PARITY.md); a capability is marked complete there only when it is
reachable from both supported products or explicitly documented as
platform-specific.

## Phase 0 — architecture bootstrap

- [x] Java 21 multi-module core with allowlisted platform UI libraries only
- [x] typed plugin, capability, contribution, and lifecycle contracts
- [x] validated transactional plugin runtime
- [x] first Library vertical slice
- [x] desktop shell and Android host seam
- [x] repository-specific AnilibJava quality checker
- [x] repository-wide no-Javadoc/KDoc convention enforced by AnilibJava
- [x] architecture smoke suite

## Phase 1 — local library

- [x] durable file-backed catalog with atomic writes and migrations
- [x] categories, favourites, history, progress, and per-title metadata
- [x] local archive/folder source
- [x] cover cache and JDK image decoding
- [x] desktop library, details, and history pages
- [x] Android application shell backed by the same capabilities

## Phase 2 — sources and discovery

- [x] stable source extension SDK
- [x] JDK HTTP client, cookies, rate limits, and cache contracts
- [x] browse, search, filters, migration, and source preferences
- [x] isolated source bundles with explicit permissions

## Phase 3 — reader and downloads

- [x] reader models, page pipeline, prefetch, and reading directions
- [x] download queue, resumable jobs, storage policies, and offline mode
- [x] backup/restore with versioned, self-owned codecs

## Phase 4 — video and tracking

- [x] episode model, stream selection, subtitles, and playback state
- [x] platform media backends behind a narrow player capability
- [x] tracker SDK and opt-in tracker bundles
- [x] background library updates and notifications

## Phase 5 — release products

- [x] reproducible desktop packages for Windows, Linux, and macOS
- [x] Android APK using only Android SDK/platform APIs beyond shared Anilib code
- [x] extension signing, compatibility metadata, and update channels
- [x] import compatibility for user-owned Aniyomi backups where legally and
  technically feasible

## Phase 6 — bring-your-own sources and complete settings

- [x] user-managed extension repository URLs using the Aniyomi index shape
- [x] durable extension language filters and pinned package ordering on Android
  and desktop
- [x] durable Browse language filters and pinned source ordering on Android and
  desktop
- [x] preserve arbitrary printable `pkg` identities without vendor-prefix rules,
  using path-safe hashed artifact storage
- [x] resolve GitHub repository URLs dynamically through default-branch and
  publication-branch `index.min.json`/`index.json` candidates on Android and desktop
- [x] official `fr.vriege.anilib` source template compiled as an isolated portable module
- [x] dependency-free SourcePublisher for reproducible JAR packaging, Ed25519 keys,
  SHA-256 checksums, signatures, and deterministic full/minified indexes
- [x] ready-to-run GitHub Actions publication workflow targeting a `repo` branch
- [x] end-to-end example that packages, signs, installs, restarts, and browses the
  same source through the Android/desktop product graph
- [x] signed portable Anilib artifacts with trust, install, update, disable, and remove
- [x] shared manual and opt-in six-hour automatic source update channels, restricted
  to the installed package identity and Ed25519 publisher key
- [x] load enabled portable Bundles into the Source registry through an explicit
  restart-isolated module boundary
- [x] Android-only HTTPS download and user-confirmed PackageInstaller hand-off
  for user-supplied Aniyomi extension APKs
- [x] best-effort Android discovery and metadata compatibility for visible Aniyomi APKs
- [x] explicit per-package signing-certificate trust and host-ABI preflight
  without loading APK extension classes
- [x] Anilib-owned extension contracts with APK compatibility isolated to
  Android and portable Bundles shared by Android and desktop
- [x] pre-start Android adapter from ABI-ready anime APK catalogues, episodes, streams,
  headers, and subtitles into explicit Anilib Source Bundles
- [ ] execute discovered Aniyomi sources through a compatibility runtime without
  importing Aniyomi's external host dependency graph into shared Anilib modules
  - [x] adapt ext-lib 17 suspend catalogue, combined episode-update, hoster, and
    video calls while retaining the ext-lib 16 RxJava path
  - [ ] provide the exact Android host classes and external runtime ABI required
    to load current extension bytecode
  - [x] project text, checkbox, tri-state, select, sort, and grouped extension
    filters into shared Anilib models and apply selected values during search
  - [x] project switch, text, and select source preferences into shared Anilib
    models and apply their values before extension requests
  - [x] discover manga extension APK metadata and preflight its host ABI separately
    from the anime contract
  - [x] add the equivalent trusted APK bridge for manga catalogue, chapters,
    reader pages, filters, preferences, and bounded extension-client downloads
- [x] shared Settings Bundle with durable appearance and feature-policy preferences
- [x] live Android and desktop theme selection from shared settings
- [x] enforce Wi-Fi, incognito, and adult-content policies in their owning Bundles
- [x] user-confirmed clear actions for shared HTTP cache, HTTP cookies, and WebView cookies
- [x] clear WebView cache/storage immediately on Android and before the next KCEF startup
- [x] clean unused application database entries
- [x] source and title WebView with shared browser-session cookies
- [x] source-declared WebView headers, User-Agent, and challenge-cookie completion
- [ ] per-screen parity audit against the current Aniyomi information architecture
  - [x] pin the reference revision and inventory every user-facing screen group
  - [x] audit the application shell and More hub, replacing inert category,
    statistics, and About rows with working shared screens
  - [ ] audit and align Library, details, Updates, and History
  - [ ] audit and align Browse, source catalogues, migration, and extensions
  - [ ] audit and align Reader, Player, Downloads, Backup, Tracking, and WebView
  - [ ] audit and align every Settings destination, About, Help, and app updates

Each checkbox should land through a Bundle that can be removed from the
Standard configuration without changing unrelated features.
