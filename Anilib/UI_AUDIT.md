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
| Updates | [x] | Partial | Scheduling, refresh/cancel, failures, progress, read state, and anime/manga/unread filters are live; add date grouping, selection actions, skipped reasons, and per-item download controls |
| History | [x] | Complete | Search, Today/Yesterday/date grouping, anime/manga filters and labels, title navigation, media-aware resume, and targeted removal are live |
| Browse landing and sources | [x] | Complete | Anime/manga source, extension, and migration tabs expose update-count badges, fixed-density rows, source/extension overflow actions, global search, languages, pinning, latest, WebView, and repository navigation |
| Source catalogue, search, and filters | [x] | Partial | Popular/latest, paging, search, filters, source preferences, list/grid, web entry, and library admission are live; add item menus and persisted display choice |
| Migration | [x] | Partial | Source/title selection and migration are live; add batch options, seasonal anime handling, comparison detail, and progress states |
| Extensions and repositories | [x] | Partial | Repository catalogue, language/pin ordering, trust, install/update/enable/remove, APK hand-off, source navigation, Browse update badges, in-place update progress, and errors are live; add a dedicated extension detail route |
| More hub | [x] | Partial | Core ordering and actions are live; add storage and help destinations plus richer download state |
| Categories | [x] | Partial | The shared screen shows real counts plus create, rename, reorder, delete, display, and update policies |
| Statistics | [x] | Complete | The shared screen reports real totals plus status, source, language, tracker-score bands, watched/known duration, average progress, and 7/30/365-day activity |
| Downloads | [x] | Partial | Durable queue, storage usage, offline mode, pause/resume/cancel/remove, errors, and status filters are live; add grouping, reorder, storage location, delete-all, and speed/ETA states |
| Backup and restore | [x] | Partial | Creation, inspection, confirmed restore/delete, rollback, and Anilib/Aniyomi import are live; add automatic scheduling, destinations, content selection, and sharing |
| Tracking | [x] | Partial | Account authentication seam, search/bind/edit/refresh/remove, progress, score, dates, and privacy are live; add first-party provider bundles, icons, richer dialogs, and automatic sync preferences |
| Settings home | [x] | Partial | Search and dedicated General, Appearance, Privacy, Library, Reader, Player, Downloads, Services, Data/storage, and About routes are live; add exact Aniyomi icons and summary density |
| Appearance settings | [x] | Partial | Live system/light/dark selection and a persistent Library/Updates/History/Browse/More start screen are available; add language packs, theme families, colors, typography, and navigation style |
| Library and update settings | [x] | Partial | Wi-Fi policies and the complete update schedule/skip controls are live; add category defaults, exceptions, and automatic-download depth |
| Reader settings and reader | [x] | Partial | Immersive pages, resume, tap zones, slider, LTR/RTL/vertical/webtoon, prefetch, and retry are live; add scaling, crop/split, rotation, color filters, transitions, and per-title overrides |
| Player settings and player | [x] | Partial | Searchable/unwatched episode list, qualities, subtitles, resume, protected media relay, and native Android/desktop playback are live; add complete controls, gestures, decoder/audio policy, PiP, and custom buttons |
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
- [x] Verified catalogue paging, global/per-source search, filters, preferences, WebView entry, and migration routes.
- [x] Added episode search/unwatched filtering, download status filtering, and reader page retry.
- [x] Verified backup inspection/rollback/import, tracking edit flows, and browser cookie/challenge handling.
- [x] Added settings search and connected Sources, Tracking, Backup, and About to their working screens.
- [x] Added one shared stable application-update check with version comparison and release-page hand-off.
- [x] Split Settings into searchable dedicated destinations and added a durable start-screen choice.
- [x] Kept every result shared by Android and desktop; no feature behavior was copied into a platform launcher.

The audit is intentionally grouped by user workflow. Aniyomi currently spreads
some workflows over separate anime and manga route classes; Anilib may share one
adaptive screen only when both variants retain the same reachable behavior.
