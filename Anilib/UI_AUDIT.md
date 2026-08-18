# UI parity audit

This audit compares the shared Anilib interface with Aniyomi commit
`89b5aba571f52b714c305581fa78d32c658317fc` from 2026-08-17. Android and
desktop render the same Compose screens, so one result applies to both products
unless a row explicitly names a platform adapter.

`Audited` means that the reachable screens, actions, and empty/error/loading
states were compared. It does not mean complete parity. The result column stays
explicit so an architectural seam cannot be mistaken for a finished workflow.

| Screen group | Audited | Result | Next user-visible gap |
| --- | --- | --- | --- |
| Application shell and primary navigation | [x] | Partial | Add update/source badges, configurable start screen, and tab reselection behavior |
| Library | [x] | Partial | Search, filters, persisted display choices, multi-selection, and bulk category/favourite/download/migrate/delete actions are live |
| Anime and manga details | [x] | Complete | Shared artwork, facts, editable metadata, chapter/episode lists, related titles, reader/player/download/tracking actions, sharing, and title/source WebView entry points are live |
| Updates | [x] | Complete | Explicit scheduling, refresh/cancel, failures, progress, anime/manga/unread filters, date grouping, selection read/remove/exclude/download actions, per-item downloads, and actionable skipped-reason exceptions are live |
| History | [x] | Complete | Search, Today/Yesterday/date grouping, anime/manga filters and labels, title navigation, media-aware resume, and targeted removal are live |
| Browse landing and sources | [x] | Complete | Anime/manga source, extension, and migration tabs expose update-count badges, fixed-density rows, source/extension overflow actions, global search, languages, pinning, latest, WebView, and repository navigation |
| Source catalogue, search, and filters | [x] | Complete | Popular/latest, paging, search, filters, source preferences, per-source persisted list/grid display, item menus, WebView entry, and library admission are live |
| Migration | [x] | Complete | Multi-title selection, target-source comparison, seasonal anime matching, title options, candidate preview, progress, cancellation, and retry of partial failures are live |
| Extensions and repositories | [x] | Partial | Repository catalogue, language/pin ordering, dedicated permission/trust/version/changelog details, install/update progress with retryable diagnostics, enable/remove, APK hand-off, source navigation, and Browse update badges are live; add full per-source management parity |
| More hub | [x] | Partial | Core ordering and actions are live; add storage and help destinations plus richer download state |
| Categories | [x] | Partial | The shared screen shows real counts plus create, rename, reorder, delete, display, and update policies |
| Statistics | [x] | Complete | The shared screen reports real totals plus status, source, language, tracker-score bands, watched/known duration, average progress, and 7/30/365-day activity |
| Downloads | [x] | Complete | Durable grouped priority queue, reorder/delete-all, speed/ETA, partial restart, validated storage migration, index repair, per-title management, storage usage, offline mode, controls, errors, status filters, and automatic category/media-limit/cleanup policies are live |
| Backup and restore | [x] | Partial | Creation, inspection, confirmed restore/delete, rollback, and Anilib/Aniyomi import are live; add automatic scheduling, destinations, content selection, and sharing |
| Tracking | [x] | Partial | Opt-in AniList and Kitsu authentication, search/bind/edit/refresh/remove, progress, score, dates, and privacy are live; add provider icons, richer dialogs, conflict handling, and automatic sync preferences |
| Settings home | [x] | Partial | Search and dedicated General, Appearance, Privacy, Library, Reader, Player, Downloads, Services, Data/storage, and About routes are live; add exact Aniyomi icons and summary density |
| Appearance settings | [x] | Partial | Live system/light/dark selection and a persistent Library/Updates/History/Browse/More start screen are available; add language packs, theme families, colors, typography, and navigation style |
| Library and update settings | [x] | Partial | Wi-Fi policies, automatic download category/media-limit/cleanup rules, full update scheduling, skip controls, and per-title exceptions are live; add category defaults |
| Reader settings and reader | [x] | Complete | Immersive pages, resume, slider, navigation modes, persisted gestures/display profiles, filters, transitions, Android orientation, chapter lists and navigation, durable read state, exact downloads, and complete menus are live |
| Player settings and player | [x] | Complete | Searchable episodes, protected native playback, complete controls and gestures, persistent policies, intro/outro skipping, Android PiP/background audio, and capability-negotiated desktop controls are live |
| Data, storage, and advanced settings | [x] | Partial | A dedicated page owns confirmed cookie/cache/WebView/database cleanup plus backup and About routes; add storage inspection, user agent, proxy/DoH, diagnostics, and crash logs |
| WebView | [x] | Partial | Navigation, reload, progress, source headers/User-Agent, cookie transfer, challenge completion, and platform cleanup are live; add automatic challenge retry, file chooser, pop-ups, downloads, and browser settings |
| About, help, and application updates | [x] | Partial | Version, platform, stable-channel check, release-page hand-off, project, and issue links are live; add licences, changelog, automatic installation, and channel choice |

## Completed in the first pass

- [x] Verified the five primary destinations and adaptive bottom-bar/navigation-rail layout.
- [x] Removed inert `Categories`, `Statistics`, and `About` rows from the More hub.
- [x] Added working shared category counts, library statistics, and About screens.
- [x] Added live downloaded-only and incognito switches to More.
- [x] Added a live pending-download summary to More.
- [x] Added library search, media/favourite/category filters, and deterministic title sorting.
- [x] Added persisted grid/list density and sorting, category landing, full category CRUD, and per-category policies.
- [x] Added library multi-selection with bulk category, favourite, download, migration, and confirmed deletion.
- [x] Added searchable history plus anime/manga/unread update filters.
- [x] Added conventional back navigation to title details.
- [x] Connected Browse extension tabs directly to repository management.
- [x] Added dedicated extension details with permissions, trust identity, versions, changelog, progress, retry, and failure diagnostics.
- [x] Verified catalogue paging, global/per-source search, filters, preferences, WebView entry, and migration routes.
- [x] Persisted each source catalogue's grid/list choice and added item action menus.
- [x] Completed batch migration with seasonal matching, source comparison, preview, progress, cancellation, and partial-failure retry.
- [x] Added synthetic offline compatibility fixtures for the public Yuzono and Keiyoushi repository JSON shapes.
- [x] Added persisted Reader interaction mappings for tap zones, swipes, double taps, and long presses.
- [x] Added persisted Reader scale, fit, border crop, split/dual-page, rotation, and webtoon-spacing controls.
- [x] Added Reader color/brightness filters, transitions, Android orientation policy, and durable per-title overrides.
- [x] Added Reader chapter lists/navigation, durable read/unread actions, exact chapter downloads, and complete menus.
- [x] Added complete Player transport controls, gestures, speed/orientation, brightness/volume, lock, and configurable buttons.
- [x] Added persistent Player decoder/audio/subtitle/quality policies, per-title overrides, and intro/outro skipping.
- [x] Added Android automatic/manual picture-in-picture and explicit background-audio service behavior.
- [x] Added desktop loop/restart controls and an mpv-compatible negotiated contract for frame, delay, aspect, and deinterlace commands.
- [x] Added episode search/unwatched filtering, download status filtering, and reader page retry.
- [x] Added grouped priority downloads, manual ordering, delete-all, live speed/ETA, and explicit partial recovery.
- [x] Verified backup inspection/rollback/import, tracking edit flows, and browser cookie/challenge handling.
- [x] Added settings search and connected Sources, Tracking, Backup, and About to their working screens.
- [x] Added one shared stable application-update check with version comparison and release-page hand-off.
- [x] Split Settings into searchable dedicated destinations and added a durable start-screen choice.
- [x] Kept every result shared by Android and desktop; no feature behavior was copied into a platform launcher.

The audit is intentionally grouped by user workflow. Aniyomi currently spreads
some workflows over separate anime and manga route classes; Anilib may share one
adaptive screen only when both variants retain the same reachable behavior.
