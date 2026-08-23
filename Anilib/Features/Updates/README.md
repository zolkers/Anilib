# Updates feature

Updates is the removable Aniyomi-style library refresh vertical. Shared Java
owns one non-overlapping job, durable scheduling policy, per-title source
baselines and exceptions, a bounded selectable unread feed, backup data, and platform-neutral progress
notifications. The Desktop application renders the presentation model.

## Refresh behavior

- titles are filtered by favourite, publication/progress state, and included or
  excluded categories;
- each source is queried sequentially while up to five different sources run in
  parallel, so one extension is never flooded with concurrent title requests;
- the first successful query establishes a silent baseline, and later source
  identities become chapter or episode events;
- cancellation, per-title failures, progress, last-run time, and next-run time
  are visible through one immutable snapshot;
- excluded titles expose typed skip reasons and can be promoted to durable
  per-title exceptions;
- the shared feed groups discoveries by date and supports exact downloads,
  selection, read/unread, removal, and exclusion actions;
- policy, baselines, feed read state, and last-run time use atomic bounded
  persistence and the feature-owned `library-updates` backup section.

## Platform boundary

`LibraryUpdateNotifier` carries only toolkit-neutral messages. Desktop maps it
to the system tray while the shared durable policy remains independent from the
window lifecycle.

The Bundle remains the only selected unit. Removing `UpdatePlugin` removes the
service, presentation, backup codec, scheduler, and notifier capability without
changing Library, Source, Backup, or the Desktop shell.
