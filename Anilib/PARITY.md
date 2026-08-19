# Aniyomi parity matrix

This document separates implemented user behavior from architectural seams.
`ROADMAP.md` is the authoritative forward work queue and newcomer hand-off;
[`UI_AUDIT.md`](UI_AUDIT.md) records the pinned screen-by-screen comparison;
this matrix is the current product-level truth. `Partial` means usable behavior
exists but the corresponding Aniyomi workflow or settings depth is incomplete.

| Area | State | Current Anilib behavior | Remaining parity work |
| --- | --- | --- | --- |
| Library | Partial | Durable user-selected shortcuts, cover grid/list, categories, favourites, progress, filters, persisted display choices, multi-selection and bulk actions | Finish visual density/count badges and validate every detail/bulk journey on packaged Android and desktop |
| Browse and sources | Partial | Popular/latest, cover grid/list, search, persisted display choice, source preferences, autonomous source-title details, episodes/chapters, direct transient Reader/Player opening, WebView entry, and explicit Library shortcut admission | Finish extension filter transport, infinite paging, and packaged-host validation |
| Reader | Partial | LTR, RTL, vertical, webtoon, resume, prefetch, local/remote pages, persisted gestures and display profiles, filters/transitions/orientation, chapter list, cross-chapter navigation, and non-persistent Browse sessions | Validate the transient and Library journeys on packaged hosts |
| Player | Partial | Episodes, qualities, subtitles, resume, protected streams, controls and gestures, playback policies, Android PiP/background audio, desktop controls, and non-persistent Browse sessions | Validate playback from Browse on packaged hosts |
| Downloads | Complete | Durable grouped priority queue, reorder/delete-all, speed/ETA, partial recovery, pause/resume/cancel, quotas, offline fallback, validated custom storage with migration, index repair, per-title actions, and durable automatic rules with category/media limits and cleanup policies | — |
| Tracking | Complete | Versioned adapter SDK, opt-in AniList and Kitsu Bundles, branded provider identities, validated search/edit flows, durable mirrors, automatic/manual bidirectional sync, persisted direction/conflict preferences, and explicit conflict resolution | — |
| Updates | Complete | Scheduled non-overlapping updates, all interval controls, category and per-title exceptions, typed skip reasons, date-grouped selectable events, exact per-item downloads, read/remove actions, notifications, and Android process-death scheduling | — |
| Backup | Complete | Versioned Anilib archive, daily/weekly automation, atomic policy persistence, content selection, retention, configurable native destinations, Android SAF/desktop export and file sharing, bounded Aniyomi import, previews, checksums, and rollback | — |
| Local source | Complete | Aniyomi `local`/`localanime` roots, strict details/chapters/episodes JSON, covers, ordered folder/ZIP/CBZ/EPUB manga chapters, MP4/MKV episodes, thumbnails, sidecar subtitles, atomic index, and explicit re-scan | — |
| Extension repositories | Partial | User-managed HTTPS indexes or GitHub repository URLs, dynamic default-branch JSON discovery, shared catalogue, dedicated permission/trust/version/changelog details, retryable install diagnostics, durable language filters and pinned ordering, Ed25519 publisher trust, SHA-256/signature/API/archive verification, durable lifecycle actions, and restart-isolated JPMS loading into the Android/desktop Source registry | Full per-source management parity |
| APK extensions | Partial | Android HTTPS hand-off, package discovery, certificate trust and host-ABI preflight; optional desktop checksum-pinned JVM sidecar with repository sync, immediate leaf-Bundle activation, installed-state tracking, catalogue/search/latest/details/preferences, chapters/pages, episodes and loopback-relayed streams | Extension-defined filters are not transported by Miwayomi 0.2.9; real packaged-host journeys remain to be executed |
| Settings | Complete | Removable Settings Bundle, atomic persistence, dedicated shared destinations, complete appearance and browser policy, category defaults/exceptions, enforced policies, bounded diagnostics, native export, allowlisted two-phase resets, complete English/French shared UI packs, and reachable privacy/licence/third-party notices | — |
| Network maintenance | Complete | Durable live User-Agent, HTTP proxy, RFC 8484 DNS-over-HTTPS resolution gate, timeout and response-cache policy; bounded per-source diagnostics; shared cookies/rate limits; and confirmed Android/desktop cleanup actions | — |
| WebView | Complete | Source API 1.6 source/title entry points, durable browser policy, Android System WebView, desktop KCEF, navigation controls, bidirectional cookies, per-source headers/User-Agent, platform file chooser/pop-up/download handling, and automatic cookie-driven challenge retry without provider code | — |
| Release products | Complete | MSI/DEB/DMG and Android APK publish atomically from stable or beta tags with production signing, SHA-256, GitHub provenance, and an Ed25519-signed update manifest; the app verifies, downloads, and hands installers to each operating system | — |

## Compatibility policy

Aniyomi repository metadata is a transport compatibility target, not permission
to redistribute an extension catalogue. Anilib ships with no third-party source
repository and accepts only URLs entered by the user. Repository owners remain
responsible for their content and binaries.

Existing Aniyomi extensions are Android APKs compiled against Android and
Aniyomi-specific APIs. Android provides a constrained best-effort bridge;
desktop delegates them to an optional isolated JVM compatibility engine rather
than loading their bytecode into Anilib. Cross-platform sources
use signed Anilib Bundles against the Java Source SDK. Repository entries may
publish both artifacts so one user-supplied index serves Android compatibility
and portable Anilib products.
