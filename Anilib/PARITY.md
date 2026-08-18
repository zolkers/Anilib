# Aniyomi parity matrix

This document separates implemented user behavior from architectural seams.
`ROADMAP.md` is the authoritative forward work queue and newcomer hand-off;
[`UI_AUDIT.md`](UI_AUDIT.md) records the pinned screen-by-screen comparison;
this matrix is the current product-level truth. `Partial` means usable behavior
exists but the corresponding Aniyomi workflow or settings depth is incomplete.

| Area | State | Current Anilib behavior | Remaining parity work |
| --- | --- | --- | --- |
| Library | Complete | Durable anime/manga titles, category CRUD and policies, favourites, progress, filters, persisted display choices, multi-selection and bulk actions, complete title details, date-grouped actionable history, and statistics by status/source/language/score/duration/progress/activity | — |
| Browse and sources | Complete | Popular/latest, paging, search, filters, preferences, per-source persisted grid/list display, catalogue item menus, batch migration with seasonal matching/comparison/options/preview/progress/recovery, installed-extension metadata and details, durable language filters, pinned ordering, update badges/actions, source/extension overflow menus, fixed row density, retryable loading/error states, and offline compatibility fixtures for public Yuzono/Keiyoushi index shapes | — |
| Reader | Complete | LTR, RTL, vertical, webtoon, resume, prefetch, local/remote pages, persisted gestures and display profiles, filters/transitions/orientation, chapter list and cross-chapter navigation, durable read state, exact chapter downloads, and complete in-reader menus | — |
| Player | Complete | Episodes, qualities, subtitles, resume, protected streams, complete controls and gestures, durable playback policies and per-title overrides, intro/outro skipping, Android PiP/background audio, and capability-negotiated desktop controls compatible with richer mpv-style backends | — |
| Downloads | Complete | Durable grouped priority queue, reorder/delete-all, speed/ETA, partial recovery, pause/resume/cancel, quotas, offline fallback, validated custom storage with migration, index repair, per-title actions, and durable automatic rules with category/media limits and cleanup policies | — |
| Tracking | Complete | Versioned adapter SDK, opt-in AniList and Kitsu Bundles, branded provider identities, validated search/edit flows, durable mirrors, automatic/manual bidirectional sync, persisted direction/conflict preferences, and explicit conflict resolution | — |
| Updates | Complete | Scheduled non-overlapping updates, all interval controls, category and per-title exceptions, typed skip reasons, date-grouped selectable events, exact per-item downloads, read/remove actions, notifications, and Android process-death scheduling | — |
| Backup | Complete | Versioned Anilib archive, daily/weekly automation, atomic policy persistence, content selection, retention, configurable native destinations, Android SAF/desktop export and file sharing, bounded Aniyomi import, previews, checksums, and rollback | — |
| Local source | Partial | Local folders and CBZ reading | Full Aniyomi folder conventions, local anime metadata, covers, episode JSON, and rescan actions |
| Extension repositories | Partial | User-managed HTTPS indexes or GitHub repository URLs, dynamic default-branch JSON discovery, shared catalogue, dedicated permission/trust/version/changelog details, retryable install diagnostics, durable language filters and pinned ordering, Ed25519 publisher trust, SHA-256/signature/API/archive verification, durable lifecycle actions, and restart-isolated JPMS loading into the Android/desktop Source registry | Full per-source management parity |
| APK extensions (Android) | Partial | HTTPS APK hand-off, package discovery, certificate trust, host-ABI preflight, and isolated best-effort anime/manga adapters | Bytecode requiring unavailable Aniyomi host classes is intentionally rejected; portable Anilib Bundles are the active Android/desktop source format |
| Settings | Partial | Removable Settings Bundle, atomic persistence, dedicated shared destinations, selectable language packs, theme mode/family/accent, typography scale, adaptive/forced navigation, category defaults and exceptions, enforced adult/incognito/Wi-Fi policies, and confirmed maintenance actions | Complete network, diagnostics, WebView, and localization depth listed in the UI audit |
| Network maintenance | Complete | Durable live User-Agent, HTTP proxy, RFC 8484 DNS-over-HTTPS resolution gate, timeout and response-cache policy; bounded per-source diagnostics; shared cookies/rate limits; and confirmed Android/desktop cleanup actions | — |
| WebView | Partial | Source API 1.6 source/title entry points, Android System WebView, desktop KCEF, navigation controls, bidirectional shared-cookie handoff, per-source headers/User-Agent, challenge-cookie verification, and confirmed data clearing | Automatic provider-specific challenge retry, file chooser/pop-up/download handling, and browser settings |
| Release products | Partial | MSI/DEB/DMG and Android APK publish atomically from version tags; production requires Android signing, Windows Authenticode, macOS signing/notarization, SHA-256 verification, and signed provenance; the app checks the stable GitHub channel | Automatic in-application download and installation |

## Compatibility policy

Aniyomi repository metadata is a transport compatibility target, not permission
to redistribute an extension catalogue. Anilib ships with no third-party source
repository and accepts only URLs entered by the user. Repository owners remain
responsible for their content and binaries.

Existing Aniyomi extensions are Android APKs compiled against Android and
Aniyomi-specific APIs. Android may provide a constrained, best-effort APK
bridge, but desktop cannot execute those APKs directly. Cross-platform sources
use signed Anilib Bundles against the Java Source SDK. Repository entries may
publish both artifacts so one user-supplied index serves Android compatibility
and portable Anilib products.
