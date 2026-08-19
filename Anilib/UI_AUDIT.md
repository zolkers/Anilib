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
| Library | [x] | Partial | Cover grid/list and shortcut-only ownership are live; validate density, badges, empty states, and bulk actions against the reference on both packaged hosts |
| Anime and manga details | [x] | Partial | Source artwork, metadata, episode/chapter lists, and direct non-persistent Reader/Player opening no longer require Library admission; packaged-host visual validation remains |
| Updates | [x] | Complete | Explicit scheduling, refresh/cancel, failures, progress, anime/manga/unread filters, date grouping, selection read/remove/exclude/download actions, per-item downloads, and actionable skipped-reason exceptions are live |
| History | [x] | Complete | Search, Today/Yesterday/date grouping, anime/manga filters and labels, title navigation, media-aware resume, and targeted removal are live |
| Browse landing and sources | [x] | Complete | Anime/manga source, extension, and migration tabs expose update-count badges, fixed-density rows, source/extension overflow actions, global search, languages, pinning, latest, WebView, and repository navigation |
| Source catalogue, search, and filters | [x] | Partial | Popular/latest chips, cover grid/list, search, source preferences, title navigation, direct playback/reading, WebView entry, and independent shortcut admission are live; add infinite paging and sidecar filter transport |
| Local source | [x] | Complete | Aniyomi-compatible manga/anime roots expose metadata, covers, ordered chapters/episodes, thumbnails, subtitles, and an explicit atomic re-scan action |
| Migration | [x] | Complete | Multi-title selection, target-source comparison, seasonal anime matching, title options, candidate preview, progress, cancellation, and retry of partial failures are live |
| Extensions and repositories | [x] | Partial | Repository catalogue, scrollable language/pin ordering with immediate filled-pin feedback, repository artwork, platform-specific install actions and explanations, permission/trust/version/changelog details, progress, retry diagnostics, enable/remove, source navigation, and Browse update badges are live; add full per-source management parity |
| More hub | [x] | Complete | Quick filters, Library, Services, and Application are grouped in rounded surfaces with distinct vector icons and summaries; repository, tracking, backup, and settings routes have one top-level home each |
| Categories | [x] | Partial | The shared screen shows real counts plus create, rename, reorder, delete, display, and update policies |
| Statistics | [x] | Complete | The shared screen reports real totals plus status, source, language, tracker-score bands, watched/known duration, average progress, and 7/30/365-day activity |
| Downloads | [x] | Complete | Durable grouped priority queue, reorder/delete-all, speed/ETA, partial restart, validated storage migration, index repair, per-title management, storage usage, offline mode, controls, errors, status filters, and automatic category/media-limit/cleanup policies are live |
| Backup and restore | [x] | Complete | Creation, daily/weekly scheduling, selected content, retention, configurable folders, inspection, confirmed restore/delete, rollback, Anilib/Aniyomi import, Android SAF/desktop export, and file sharing are live |
| Tracking | [x] | Complete | Opt-in AniList and Kitsu authentication, branded provider icons, confirmed search/bind/remove, validated editing, progress/score/dates/privacy, automatic/manual bidirectional sync, durable preferences, and explicit conflict resolution are live |
| Settings home | [x] | Complete | Searchable Application, Library/media, and Advanced groups use rounded containers, compact summaries, distinct leading vector icons, and explicit navigation affordances; service destinations live only in More and every interactive detail row has a vector icon |
| Appearance settings | [x] | Complete | Persistent complete English/French language packs, theme mode/family/accent palettes, typography scale, adaptive/bottom/rail navigation, and start-screen selection apply live |
| Library and update settings | [x] | Complete | Wi-Fi policies, category defaults and per-category display/update policies, automatic download rules, full update scheduling, skip controls, and per-title exceptions are live |
| Reader settings and reader | [x] | Complete | Immersive pages, resume, slider, navigation modes, persisted gestures/display profiles, filters, transitions, Android orientation, chapter lists and navigation, durable read state, exact downloads, and complete menus are live |
| Player settings and player | [x] | Complete | Searchable episodes, protected native playback, complete controls and gestures, persistent policies, intro/outro skipping, Android PiP/background audio, and capability-negotiated desktop controls are live |
| Data, storage, and advanced settings | [x] | Complete | Durable network policy, per-source diagnostics, bounded storage inspection, logs/crash reports, native ZIP export, allowlisted plan/confirm resets, cleanup, backup, and About routes are live |
| WebView | [x] | Complete | Navigation, reload, progress, durable JavaScript/DOM/files/pop-up/download/text-zoom policy, cookie transfer, platform handlers, cleanup, and cookie-driven automatic challenge retry are live |
| About, help, and application updates | [x] | Complete | Version, platform, stable/beta choice, signed download verification, progress, changelog, licence, source commit, release links, and platform-owned installation hand-off are live |

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
- [x] Added stable/beta application-update checks, signed downloads, changelog/licence presentation, and platform-owned installation.
- [x] Split Settings into searchable dedicated destinations and added a durable start-screen choice.
- [x] Aligned Settings with the pinned Aniyomi visual system using grouped surfaces, compact summaries, leading vector icons, and trailing navigation affordances.
- [x] Made source and extension language selectors bounded and scrollable with full-row selection targets.
- [x] Loaded repository `icon/<package>.png` extension artwork through the shared cached HTTP pipeline with a bounded vector fallback.
- [x] Added platform-aware extension installation actions, an actionable empty Browse state, and immediate filled/outlined pin feedback.
- [x] Rebuilt More with the same grouped vector-card system as Settings and removed duplicated top-level service routes.
- [x] Localized every shared UI label and icon description in the advertised English/French packs and added a repository gate against untranslated additions.
- [x] Added deterministic pixel and semantics captures with real clicks for the shared navigation routes at compact and expanded sizes.
- [x] Added content-rich local manga, reader, anime, player, catalogue, and offline WebView fixtures to both adaptive capture sizes.
- [x] Kept every result shared by Android and desktop; no feature behavior was copied into a platform launcher.

The audit is intentionally grouped by user workflow. Aniyomi currently spreads
some workflows over separate anime and manga route classes; Anilib may share one
adaptive screen only when both variants retain the same reachable behavior.
