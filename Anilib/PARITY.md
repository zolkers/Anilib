# Aniyomi parity matrix

This document separates implemented user behavior from architectural seams.
`ROADMAP.md` tracks large delivery milestones; this matrix is the product-level
truth. “Partial” means usable behavior exists but the corresponding Aniyomi
workflow or settings depth is not yet complete.

| Area | State | Current Anilib behavior | Remaining parity work |
| --- | --- | --- | --- |
| Library | Partial | Durable anime/manga titles, categories, favourites, history, progress, filters | Complete all display modes, bulk actions, per-category policy, statistics, and metadata editing |
| Browse and sources | Partial | Popular/latest, paging, search, filters, preferences, migration, installed-extension metadata | Repository management, installation lifecycle, language/pinning controls, and all Browse actions |
| Reader | Partial | LTR, RTL, vertical, webtoon, resume, prefetch, local/remote pages | Full viewer gestures, tap zones, scaling, crop/split, rotation, color, transitions, and per-title overrides |
| Player | Partial | Episodes, qualities, subtitles, resume, protected streams, Android/desktop playback | Full Aniyomi control surface, gestures, decoder/audio/subtitle policy, PiP, custom buttons, and mpv-compatible advanced controls |
| Downloads | Partial | Durable queue, pause/resume/cancel, quotas, offline fallback | Complete storage selection, per-title actions, download index maintenance, and all automatic-download policies |
| Tracking | Partial | Versioned adapter SDK, binding/editing UI, durable mirrors | First-party opt-in provider bundles and full login flows for Aniyomi-supported trackers |
| Updates | Partial | Scheduled non-overlapping updates, filters, events, notifications | Full update preferences, exception views, skip rules, and platform scheduling controls |
| Backup | Partial | Versioned Anilib archive, checksums, preview, merge, rollback | Automatic backup policy, storage destinations, sharing, and Aniyomi backup import |
| Local source | Partial | Local folders and CBZ reading | Full Aniyomi folder conventions, local anime metadata, covers, episode JSON, and rescan actions |
| Extension repositories | Partial | User-managed HTTPS indexes or GitHub repository URLs, dynamic default-branch JSON discovery, shared catalogue, Ed25519 publisher trust, SHA-256/signature/API/archive verification, durable lifecycle actions, and restart-isolated JPMS loading into the Android/desktop Source registry | Repository language/pinning controls and full per-source management parity |
| APK extensions (Android) | Partial | HTTPS APK download, PackageInstaller hand-off, visible-package discovery, certificate trust, host-ABI preflight, and pre-start adaptation of ABI-ready anime catalogues, episodes, streams, headers, subtitles, filters, and source preferences into Source Bundles | Supply the complete external host ABI and add manga APK compatibility; portable Anilib bundles remain the active Android/desktop format |
| Settings | Partial | Removable Settings Bundle, atomic persistence, observable snapshots, live system/light/dark theme, adult/incognito/Wi-Fi preferences, full shared hierarchy, and working network actions | Enforce stored feature policies in their owning Bundles and connect every remaining row |
| Network maintenance | Partial | Shared cookies/cache/rate limits plus confirmed Android/desktop actions to clear HTTP cookies, HTTP cache, WebView cookies, browser cache, and site storage | Default user agent, proxy/DoH policy, unused application database cleanup, and diagnostics |
| WebView | Partial | Source API 1.6 source/title entry points, Android System WebView, desktop KCEF, navigation controls, bidirectional shared-cookie handoff, per-source headers/User-Agent, challenge-cookie verification, and confirmed data clearing | Automatic provider-specific challenge retry, file chooser/pop-up/download handling, and browser settings |
| Release products | Partial | MSI/DEB/DMG matrix and Android APK pipeline | Release publication, app update channels, extension update channels, and production signing operations |

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
