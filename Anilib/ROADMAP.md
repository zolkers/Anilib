# Functional Parity Roadmap

The target is broad product parity with Aniyomi, delivered as removable vertical
features rather than a port of its internal Kotlin/Android layering.

Product parity means matching Aniyomi's visible information architecture,
navigation, screen hierarchy, labels, actions, states, settings, and behavior as
closely as each platform permits. Anilib may adapt window layout for desktop,
but it must not simplify or reinterpret an Aniyomi workflow merely because its
internal implementation is different. The refactor applies to ownership and
modularity behind the UI; Aniyomi remains the product reference.

## Phase 0 — architecture bootstrap

- [x] Java 21 multi-module core with allowlisted platform UI libraries only
- [x] typed plugin, capability, contribution, and lifecycle contracts
- [x] validated transactional plugin runtime
- [x] first Library vertical slice
- [x] desktop shell and Android host seam
- [x] repository-specific AnilibJava quality checker
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
- [ ] extension signing, compatibility metadata, and update channels
- [ ] import compatibility for user-owned Aniyomi backups where legally and
  technically feasible

Each checkbox should land through a Bundle that can be removed from the
Standard configuration without changing unrelated features.
