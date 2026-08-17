# Discovery feature

Discovery owns the Aniyomi-style Browse workflow shared by every Anilib
product. The Bundle publishes a Java `DiscoveryService` and a platform-neutral
`DiscoveryPresentation`; the adaptive Compose platform renders that same
presentation on Android and desktop.

The service provides:

- popular and latest source listings with paging;
- per-source and global search;
- validated source filters and durable source preferences;
- duplicate-safe admission into Library with a stable source origin;
- migration between sources while preserving library identity, categories,
  favourite state, progress, and history.
- installed anime/manga extension lists with declared permission and exact
  network-origin details.

Discovery does not install or scan extensions. Product configurations still
select source Bundles explicitly, and the Source registry remains the only
catalogue registry.
