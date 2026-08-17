# Functional Parity Roadmap

The target is broad product parity with Aniyomi, delivered as removable vertical
features rather than a port of its internal Kotlin/Android layering.

## Phase 0 — architecture bootstrap

- [x] Java 21 multi-module build with zero third-party libraries
- [x] typed plugin, capability, contribution, and lifecycle contracts
- [x] validated transactional plugin runtime
- [x] first Library vertical slice
- [x] desktop shell and Android host seam
- [x] repository-specific AnilibJava quality checker
- [x] architecture smoke suite

## Phase 1 — local library

- [x] durable file-backed catalog with atomic writes and migrations
- [ ] categories, favourites, history, progress, and per-title metadata
- [ ] local archive/folder source
- [ ] cover cache and JDK image decoding
- [ ] desktop library, details, and history pages
- [ ] Android application shell backed by the same capabilities

## Phase 2 — sources and discovery

- [ ] stable source extension SDK
- [ ] JDK HTTP client, cookies, rate limits, and cache contracts
- [ ] browse, search, filters, migration, and source preferences
- [ ] isolated source bundles with explicit permissions

## Phase 3 — reader and downloads

- [ ] reader models, page pipeline, prefetch, and reading directions
- [ ] download queue, resumable jobs, storage policies, and offline mode
- [ ] backup/restore with versioned, self-owned codecs

## Phase 4 — video and tracking

- [ ] episode model, stream selection, subtitles, and playback state
- [ ] platform media backends behind a narrow player capability
- [ ] tracker SDK and opt-in tracker bundles
- [ ] background library updates and notifications

## Phase 5 — release products

- [ ] reproducible desktop packages for Windows, Linux, and macOS
- [ ] Android APK using only Android SDK/platform APIs beyond shared Anilib code
- [ ] extension signing, compatibility metadata, and update channels
- [ ] import compatibility for user-owned Aniyomi backups where legally and
  technically feasible

Each checkbox should land through a Bundle that can be removed from the
Standard configuration without changing unrelated features.
