# Aniyomi parity matrix

This document separates implemented user behavior from architectural seams.
`ROADMAP.md` is the authoritative forward work queue and newcomer hand-off;
[`UI_AUDIT.md`](UI_AUDIT.md) records the pinned screen-by-screen comparison;
this matrix is the current product-level truth. `Partial` means usable behavior
exists but the corresponding Aniyomi workflow or settings depth is incomplete.

| Area | State | Current Anilib behavior | Remaining parity work |
| --- | --- | --- | --- |
| Library | Partial | Durable anime/manga titles, category CRUD and policies, favourites, history, progress, filters, persisted display choices, multi-selection and bulk category/favourite/download/migrate/delete actions | Complete statistics depth and metadata editing |
| Browse and sources | Partial | Popular/latest, paging, search, filters, preferences, migration, installed-extension metadata, durable language filters, and pinned sources/extensions | Remaining per-source management and Browse actions |
| Reader | Partial | LTR, RTL, vertical, webtoon, resume, prefetch, local/remote pages | Full viewer gestures, tap zones, scaling, crop/split, rotation, color, transitions, and per-title overrides |
| Player | Partial | Episodes, qualities, subtitles, resume, protected streams, Android/desktop playback | Full Aniyomi control surface, gestures, decoder/audio/subtitle policy, PiP, custom buttons, and mpv-compatible advanced controls |
| Downloads | Partial | Durable queue, pause/resume/cancel, quotas, offline fallback | Complete storage selection, per-title actions, download index maintenance, and all automatic-download policies |
| Tracking | Partial | Versioned adapter SDK, binding/editing UI, durable mirrors | First-party opt-in provider bundles and full login flows for Aniyomi-supported trackers |
| Updates | Partial | Scheduled non-overlapping updates, filters, events, notifications | Full update preferences, exception views, skip rules, and platform scheduling controls |
| Backup | Partial | Versioned Anilib archive plus native Android/desktop selection, bounded protobuf/gzip Aniyomi import, previews, source-aware merge, checksums, and rollback | Automatic backup policy, configurable storage destinations, and sharing |
| Local source | Partial | Local folders and CBZ reading | Full Aniyomi folder conventions, local anime metadata, covers, episode JSON, and rescan actions |
| Extension repositories | Partial | User-managed HTTPS indexes or GitHub repository URLs, dynamic default-branch JSON discovery, shared catalogue, durable language filters and pinned ordering, Ed25519 publisher trust, SHA-256/signature/API/archive verification, durable lifecycle actions, and restart-isolated JPMS loading into the Android/desktop Source registry | Full per-source management parity |
| APK extensions (Android) | Partial | HTTPS APK hand-off, package discovery, certificate trust, host-ABI preflight, and isolated best-effort anime/manga adapters | Bytecode requiring unavailable Aniyomi host classes is intentionally rejected; portable Anilib Bundles are the active Android/desktop source format |
| Settings | Partial | Removable Settings Bundle, atomic persistence, dedicated shared destinations, theme and start-screen selection, enforced adult/incognito/Wi-Fi policies, and confirmed maintenance actions | Complete the deeper option sets listed in the UI audit |
| Network maintenance | Partial | Shared cookies/cache/rate limits plus confirmed Android/desktop actions to clear HTTP cookies, HTTP cache, WebView cookies, browser cache, site storage, and orphaned feature records | Default user agent, proxy/DoH policy, and diagnostics |
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
