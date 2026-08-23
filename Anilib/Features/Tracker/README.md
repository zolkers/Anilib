# Tracker feature

Tracker is a removable vertical for Aniyomi-style external anime and manga
tracking. Shared Java owns the SDK, installed-service registry, durable title
bindings, mutations, backup codec, and platform-neutral presentation. Android
and desktop render the same account and title workflows.

## User workflow

An installed tracker exposes its authentication method, supported media kinds,
statuses, score scale, date support, and private-entry support. The shared UI
then provides:

- provider-website OAuth with an automatic loopback callback, plus sign-in
  and sign-out for password and token adapters;
- remote title search and explicit binding;
- status, fractional progress, score, start date, finish date, and privacy;
- remote refresh and confirmed adapter-owned removal;
- automatic completion semantics when synchronized progress reaches the total;
- branded provider identities and confirmed, validated search/edit/remove flows;
- manual or activity-triggered automatic synchronization with push-only,
  pull-only, and bidirectional direction policies;
- durable conflict preferences with ask, local, remote, and newest-wins
  resolution plus explicit conflict review;
- durable local mirrors and a feature-owned `tracking` backup section.

Credentials are never persisted by Tracker Core. An opted-in adapter owns its
session and secret storage policy; Anilib persists only remote title bindings
and their latest non-secret state.

## First-party providers

Anilib includes two provider Bundles selected explicitly by the Standard
product:

- AniList opens its official OAuth website, consumes the validated callback
  automatically, then provides GraphQL title search and complete list-entry
  create, update, refresh, and delete mutations;
- Kitsu uses its username/password OAuth token flow and JSON:API title search
  plus complete library-entry create, update, refresh, and delete operations.

Both providers keep credentials and access tokens in memory only. Their Bundle
manifests restrict HTTP access to the single exact provider origin, and fixture
tests exercise their full authentication and entry lifecycle without requiring
live accounts.

The Standard product reads AniList's public OAuth client identifier from the
`anilib.tracker.anilist.client-id` JVM property or the
`ANILIB_ANILIST_CLIENT_ID` environment variable. The registered provider
application must use the exact callback URI
`http://127.0.0.1:43697/oauth/anilist/callback`. Anilib binds that address only
while a login is active, opens the provider in the system browser, validates
the OAuth state and exact callback port, and stops the local listener after
completion, cancellation, or timeout. The callback can be overridden with
`anilib.tracker.anilist.callback-uri` or `ANILIB_ANILIST_CALLBACK_URI`, but it
must remain an explicit `http://127.0.0.1:<port>/path` URI and must exactly
match AniList's registered redirect. No client secret belongs in the
application, and no Anilib-hosted authentication service is involved.

Synchronization preferences and pending local changes are written atomically
beside the tracking mirror. Remote refresh timestamps let the newest-wins policy
compare provider and local state, while ask mode retains both snapshots until
the user explicitly keeps the local or remote version.

## Extension boundary

`TrackerExtensionPlugin` is the only adapter installation unit. Its
`TrackerExtensionManifest` declares one stable tracker identity and, when
needed, exact HTTP origins plus the `NETWORK` permission. The factory receives
only `TrackerExtensionContext`; its HTTP client rejects every undeclared
scheme, host, port, and redirect target.

External adapter modules use `layer=EXTENSION`, `role=BUNDLE`, and depend on
exactly one extension SDK: `feature.tracker.api` for trackers or
`feature.source.api` for sources. AnilibJava rejects direct network, filesystem,
reflection, Network feature, and Kernel access from either extension type.

The Standard product selects the Tracker, AniList, and Kitsu Bundles but no
provider account or credential. External tracker adapters remain explicit
additions to the product plugin list, so removing one bundle removes its
registration without changing Tracker Core or another feature.
