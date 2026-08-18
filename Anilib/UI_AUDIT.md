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
| Library | [ ] | Partial | Anime/manga separation, category tabs, grid/list modes, search, filters, sorting, and selection actions |
| Anime and manga details | [ ] | Partial | Artwork header, content-unit lists, status actions, related titles, metadata editing, and share/web actions |
| Updates | [ ] | Partial | Date grouping, selection actions, update errors, skipped reasons, and per-item download controls |
| History | [ ] | Partial | Search, date grouping, remove history, resume action, and separate anime/manga presentation |
| Browse landing and sources | [ ] | Partial | Match the anime/manga source and extension tabs, update badges, source menu actions, and layout density |
| Source catalogue, search, and filters | [ ] | Partial | Toolbar parity, display modes, source pinning actions, filter sheet behavior, and item menus |
| Migration | [ ] | Partial | Source selection, per-title result selection, migration options, seasonal anime handling, and progress states |
| Extensions and repositories | [ ] | Partial | Extension detail screen, source preferences navigation, trust display, install progress, and update/error actions |
| More hub | [x] | Partial | Core ordering and actions are live; add storage and help destinations plus richer download state |
| Categories | [x] | Partial | The shared screen now shows real counts; add create, rename, reorder, delete, and per-category policies |
| Statistics | [x] | Partial | The shared screen now shows real library totals; add status, language, source, score, duration, and activity charts |
| Downloads | [ ] | Partial | Per-title grouping, reorder, retry, storage location, delete-all, and detailed speed/ETA states |
| Backup and restore | [ ] | Partial | Automatic scheduling, destination management, content selection, sharing, and restore result detail |
| Tracking | [ ] | Partial | Provider login flows, account tokens, provider icons, score/date dialogs, and automatic sync preferences |
| Settings home | [ ] | Partial | Search and live destinations for every settings category |
| Appearance settings | [ ] | Partial | Language, start screen, theme families, colors, typography, navigation style, and screen-specific display choices |
| Library and update settings | [ ] | Partial | Category defaults, update restrictions, skip rules, automatic downloads, and exception handling |
| Reader settings and reader | [ ] | Partial | Gestures, tap zones, scaling, crop/split, rotation, color filters, transitions, and per-title overrides |
| Player settings and player | [ ] | Partial | Complete control surface, gestures, decoder/audio/subtitle policy, PiP, custom buttons, and advanced desktop controls |
| Data, storage, and advanced settings | [ ] | Partial | Storage inspection, default user agent, proxy/DoH, diagnostics, crash logs, and granular cleanup |
| WebView | [ ] | Partial | Challenge retry, file chooser, pop-ups, downloads, browser settings, and provider-specific behavior |
| About, help, and application updates | [ ] | Partial | Version/build metadata, licences, changelog, help links, update check, and release channel |

## Completed in the first pass

- [x] Verified the five primary destinations and adaptive bottom-bar/navigation-rail layout.
- [x] Removed inert `Categories`, `Statistics`, and `About` rows from the More hub.
- [x] Added working shared category counts, library statistics, and About screens.
- [x] Added live downloaded-only and incognito switches to More.
- [x] Added a live pending-download summary to More.
- [x] Kept every result shared by Android and desktop; no feature behavior was copied into a platform launcher.

The audit is intentionally grouped by user workflow. Aniyomi currently spreads
some workflows over separate anime and manga route classes; Anilib may share one
adaptive screen only when both variants retain the same reachable behavior.
