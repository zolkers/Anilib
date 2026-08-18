# Tracker feature

Tracker is a removable vertical for Aniyomi-style external anime and manga
tracking. Shared Java owns the SDK, installed-service registry, durable title
bindings, mutations, backup codec, and platform-neutral presentation. Android
and desktop render the same account and title workflows.

## User workflow

An installed tracker exposes its authentication method, supported media kinds,
statuses, score scale, date support, and private-entry support. The shared UI
then provides:

- sign-in and sign-out for password, token, and OAuth-code adapters;
- remote title search and explicit binding;
- status, fractional progress, score, start date, finish date, and privacy;
- remote refresh and confirmed adapter-owned removal;
- automatic completion semantics when synchronized progress reaches the total;
- durable local mirrors and a feature-owned `tracking` backup section.

Credentials are never persisted by Tracker Core. An opted-in adapter owns its
session and secret storage policy; Anilib persists only remote title bindings
and their latest non-secret state.

## First-party providers

Anilib includes two optional provider Bundles that are not selected by the
Standard product:

- AniList uses a personal access token, GraphQL title search, and complete
  list-entry create, update, refresh, and delete mutations;
- Kitsu uses its username/password OAuth token flow and JSON:API title search
  plus complete library-entry create, update, refresh, and delete operations.

Both providers keep credentials and access tokens in memory only. Their Bundle
manifests restrict HTTP access to the single exact provider origin, and fixture
tests exercise their full authentication and entry lifecycle without requiring
live accounts.

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

The Standard product selects the Tracker Bundle but no provider account.
First-party and external tracker adapters remain explicit additions to the
product plugin list, so removing one bundle removes its registration without
changing Tracker Core or another feature.
