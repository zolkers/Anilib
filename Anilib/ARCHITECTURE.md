# Anilib Architecture

Anilib uses the ownership discipline found in large modular Java applications
such as Ghidra: a small kernel, explicit module metadata, feature-owned vertical
slices, typed extension contracts, and product configurations that assemble a
fixed graph. It does not copy Ghidra APIs or dynamically scan the classpath.

## Dependency direction

An arrow means “may depend on”. Dependencies must remain explicit and acyclic.

```text
Platforms -> Configurations -> Features -> Kernel -> Foundation
     |              |             |          |
     +--------------+-------------+----------+-> Framework -> Foundation

Tooling may inspect every layer; production modules never depend on Tooling.
```

| Layer | Owns | Must not own |
| --- | --- | --- |
| `Foundation` | identities, immutable values, validation, minimal ownership primitives | feature behavior, platform types |
| `Framework` | reusable storage, HTTP, media, settings, scheduling, UI-neutral contracts | product defaults, feature policy |
| `Kernel` | plugin lifecycle, manifests, capabilities, contributions, graph resolution | library, reader, or player behavior |
| `Features` | complete vertical user capabilities and their bundles | global product selection, platform SDK types |
| `Configurations` | explicit feature selection and startup order | feature algorithms, platform adaptation |
| `Platforms` | runnable applications and Android/desktop adapters | duplicated feature behavior |
| `Tooling` | repository checks, graph validation, scaffolding, release checks | production behavior |

Every source-owning module has both a JPMS `module-info.java` and a local
`module.properties`. The former lets `javac` enforce actual access; the latter
lets AnilibJava validate architectural intent without executing production code.

## Feature shape

```text
Features/<Feature>/
  Api/       immutable public models and narrow ports
  Core/      optional domain behavior
  Runtime/   optional long-lived mechanisms
  Ui/        optional platform-neutral presentation model
  Bundle/    the feature's single AnilibPlugin composition unit
```

Only useful modules are created. A small feature does not need empty `Runtime`
or `Ui` folders. Features collaborate through an `Api`, a typed capability, or
a typed contribution point, never by importing another feature's `Core`.

## Plugins and extensions

`AnilibPlugin` is the only runtime extension unit. Its side-effect-free
`PluginManifest` declares:

- a stable component descriptor;
- required and provided typed `CapabilityKey<T>` values;
- typed `ContributionPoint<T>` values it extends.

The kernel validates the whole graph before installation. A capability has
exactly one provider. Missing providers, duplicate providers, dependency cycles,
undeclared publication, and undeclared access fail before a started product is
published.

Installation is transactional. Plugins install in dependency order. Each owns
a LIFO cleanup stack; if installation fails, the kernel closes completed
sessions in reverse order and attaches cleanup failures to the original error.
The product capability graph becomes immutable after startup. Explicit leaf
Bundles that publish no capabilities may be registered and removed at runtime;
their declared contributions and owned lifecycle resources remain transactional.

There is deliberately no classpath scanning, reflection-based injection, global
service locator, or mutable “bag of services”. Configurations select concrete
Bundle instances explicitly, which keeps addition and removal symmetrical.

Source extensions follow the same rule. The Source Bundle publishes one typed
registry and an installation-only registrar. A selected source Bundle declares
the registrar capability, registers one immutable source descriptor during
transactional installation, and owns the returned lifecycle handle. Duplicate
IDs or incompatible Source API versions therefore fail before product startup;
rollback and shutdown remove registrations automatically.

An external source Bundle declares its stable source identity and sensitive
permissions before graph validation. The Kernel supplies its factory with a
`SourceExtensionContext` containing only granted capabilities. Network access
is wrapped with exact scheme, host, and port checks; redirects are surfaced so
each hop is authorized independently. AnilibJava additionally confines modules
with `layer=EXTENSION` to the Source/HTTP contracts and rejects direct network,
filesystem, reflection, Kernel, or raw Network imports. This is a capability
boundary for product-selected modules, not a bytecode security sandbox.

User-installed portable source Bundles remain explicit graph selections.
Standard reads only enabled, checksum-matched artifacts during product startup;
their signed descriptor names the JPMS module and every Source factory, so
there is no classpath or service scan. Each archive is bounded, held in memory,
and resolved into its own child module layer against only the Source SDK. Load
failures are retained by the Extension Repository capability while other valid
Bundles continue into the immutable Kernel graph. Enable, disable, and update
therefore take effect on the next restart.

The JPMS `.jar` loader is a desktop mechanism and is never initialized on
Android, whose runtime does not provide `java.lang.module`. Android selects
installed APK source adapters explicitly; both paths still publish the same
Source contracts into the same Kernel graph.

Portable-source updates are a third explicit capability beside discovery and
installation. Android and desktop compare the same opaque package identity and
monotonic version code after repository refresh. Manual update-all still runs
the complete checksum, descriptor, API, trust, and Ed25519 verification path.
The opt-in automatic channel checks every six hours and silently accepts only a
newer Bundle signed by the exact publisher key recorded at installation. Older
store entries without publisher provenance remain manual until their next
verified update. The running Kernel graph is never mutated.

Discovery is a separate vertical feature over the Source registry and Library
catalog. Its Java service owns paging, global and per-source search, validated
filters, durable source preferences, duplicate-safe library admission, and
migration while retaining user-owned progress, history, categories, and
favourites. Its platform-neutral presentation is the only surface consumed by
the shared Compose Browse screen, so Android and desktop cannot drift into two
different discovery implementations.

Source API 1.6 owns the optional `WebSource` contract for source and title web
entry points. Discovery validates absolute HTTP(S) locations before exposing
them to the UI. Each immutable page can carry source-defined request headers,
User-Agent, and challenge-completion cookie names. The shared Compose browser
owns navigation, verifies those cookies, and transfers the resulting session
through the framework `HttpCookieJar`; only platform adapters select the
engine, using Android System WebView or desktop KCEF. Browser engine types and
lifecycle therefore remain outside Java feature code. Desktop uses KCEF's
architecture-matched runtime on x64 and ARM64 and converts initialization
failures into an unavailable browser status, so an optional WebView cannot
prevent the rest of the product from starting.

Browser-data maintenance follows the same outer boundary. The shared Settings
screen invokes a small platform controller. Android clears System WebView
cache, JavaScript storage, HTTP authentication, certificate decisions, and
view state on the UI thread. Desktop writes a bounded cleanup marker because
Chromium locks its profile while KCEF is alive; the desktop browser adapter
removes only its normalized cache subtree, without following links, before the
next KCEF initialization.

Browser behavior follows the same split. Settings persists a platform-neutral
policy for scripting, DOM storage, file selection, pop-ups, downloads, text
zoom, and challenge retry. Shared Compose applies common WebSettings and cookie
completion logic. Android alone owns Activity-result file selection and
DownloadManager; desktop alone owns CEF dialog, life-span, and download
handlers. Pop-ups are redirected into the current trusted browser surface, and
no provider name or platform SDK type crosses into a feature module.

Reader is another removable vertical over Library and Source. Its Bundle
resolves a library origin only through the typed Source registry and accepts
only sources implementing the versioned `PagedSource` contract. The shared
runtime owns validated sessions, durable progress and history, asynchronous
neighbor prefetch, defensive byte ownership, and a bounded LRU page cache.
Reading directions and controller state stay platform-neutral; Compose renders
one immersive screen on Android and desktop, while each outer adapter performs
only its native encoded-image decoding.

Downloads is a removable vertical over Library, Source, Reader, and Updates. Its Bundle
owns the durable job queue, atomic metadata writes, page files, concurrent-job
limit, per-page and total-storage policies, pause/cancel/resume transitions, and
restart reconciliation of partial jobs. Durable automatic rules select recent
content by category and media-specific limits after successful library updates;
cleanup retains a bounded latest set or removes completed read content. It
registers one typed content provider
through Reader's installation capability: completed local pages take priority,
while the original `PagedSource` remains the online fallback. Offline mode
disables that fallback without making Reader depend on Downloads, and removing
the Downloads Bundle removes both the queue capability and the registration.
The Java presentation model is rendered by one Aniyomi-style queue screen on
Android and desktop.

Backup is a removable coordinator rather than the owner of another feature's
serialization. Framework exposes the narrow `BackupSectionCodec` and prepared
restore contracts; Library and Discovery each publish an independently
versioned codec capability from their own Bundle. The Backup Bundle explicitly
requires those capabilities and assembles a bounded archive with deterministic
section order, per-section SHA-256 checksums, and a whole-archive checksum.
Creation uses atomic replacement. Restore validates every known section before
mutation, merges imported user state, commits sections in order, and rolls back
already committed sections in reverse order if a later commit fails. Unknown
future sections remain inspectable and are skipped, while malformed or
unsupported known sections fail before mutation. One shared screen on Android
and desktop provides local creation, preview, confirmed restore, and confirmed
deletion.
Backup also owns an atomically persisted daily/weekly policy: installed codecs
define selectable content, the destination is a validated non-root directory,
and retention removes only older managed archives after successful creation.
A daemon coordinator retries due work hourly and records completion only after
the archive commits. Shared UI delegates export to native desktop dialogs or
Android's Storage Access Framework; Android shares managed files through a
read-only, normalized application content provider.

The same Backup Bundle can inspect and import a user-selected Aniyomi protobuf
backup, compressed or uncompressed, without linking its serializer runtime.
The bounded wire reader maps manga, anime, categories, history, progress, and
metadata into Library's public model, discloses unsupported tracker, preference,
and embedded-extension entries before confirmation, and atomically merges by
the original numeric source identity plus title URL. APK-adapted sources use
that numeric identity too, so imported titles can reconnect without a package-
name guess. Android and desktop execute this identical Java path.

Local Source is a removable Source implementation. Its Java runtime owns an
immutable, atomically replaced index over Aniyomi-compatible `local` and
`localanime` roots, bounded dependency-free JSON parsing, file and archive
validation, metadata, covers, chapter/episode ordering, local video streams,
thumbnails, and subtitle association. Source API 1.7 exposes only a generic
optional refresh action and episode thumbnail URI; Discovery owns the shared
re-scan control and neither platform adapter interprets local folder formats.

Player is a removable vertical over Library and Source. Source API 1.4 owns the
optional `StreamingSource` extension contract and immutable episode, stream,
format, request-header, and subtitle models. The Player Bundle resolves only
anime library origins, validates source ownership and duplicate identities,
selects stream and subtitle candidates, and stores per-episode millisecond
resume state through atomic replacement. Opening and progress updates mirror
history and latest progress into Library without moving Player state ownership
there. Player also publishes its own versioned backup codec; Standard passes
that codec capability explicitly to Backup. Android and desktop render the same
episode, selection, and video surfaces. A narrow Java `PlayerBackend` capability
owns media requests, playback handles, portable state, and controls without
exposing a UI-toolkit or media-SDK type. Standard injects either an explicit
headless backend or the outer Compose adapter. That adapter uses Media3 on
Android and native desktop engines through one allowlisted ComposeMediaPlayer
surface, while progress, watched state, stream replacement, and subtitle policy
remain owned by the removable Player vertical. Header-bearing media and subtitle
URLs pass through a per-playback, tokenized loopback relay. The relay reapplies
source headers and response cookies, preserves byte ranges and redirects, and
rewrites HLS segment, key, map, and nested-playlist locations. It binds only to
`127.0.0.1`, closes with the playback handle, and never exposes an unrestricted
forward proxy.

Tracker is a removable vertical over Library. Its versioned Java SDK models
authentication, remote search, title binding, provider-supported statuses and
score scales, fractional progress, dates, privacy, refresh, removal, and
progress synchronization. Tracker adapters are explicit `AnilibPlugin` units
registered through one transactional registrar; Standard installs no hidden
provider list. A network adapter declares exact origins and receives only a
restricted HTTP client, while AnilibJava applies the same direct network,
filesystem, reflection, Network, and Kernel import bans used for source
extensions. Core stores no credentials. It atomically persists only remote
title mirrors and owns their versioned `tracking` backup section. Android and
desktop share the same account settings, search, binding, and editing surfaces.
Optional first-party AniList and Kitsu Bundles use this same boundary. AniList
starts its provider-owned OAuth website in the shared WebView, validates the
`anilib://oauth/anilist` callback and state, then performs authenticated GraphQL
operations only at `graphql.anilist.co`. Kitsu performs its password OAuth and
JSON:API lifecycle only at `kitsu.io`. Standard selects both Bundles, but no
Bundle persists its session.
Tracker Core atomically stores synchronization direction, automatic mode,
conflict policy, and pending-local markers beside its mirror. Library activity
triggers a serialized refresh only when automatic synchronization is enabled.
Remote-only changes replace the mirror, local pending changes can be pushed,
and divergent bidirectional changes are resolved by the durable policy or kept
as two explicit snapshots for user choice.

Updates is a removable vertical over Library and Source. Its single shared job
filters eligible titles, groups them by source, runs at most five source groups
in parallel, and never overlaps with itself. Atomic feature-owned state retains
the scheduling policy, per-title content baselines, recent unread discoveries,
per-title include/exclude exceptions, and the last successful run. Every title
outside the effective policy exposes a typed skip reason without persisting a
second derived index. Its versioned `library-updates` backup codec merges that
state independently. The first fetch is deliberately silent, while later
source identities become chapter or episode events. A narrow notifier port maps
progress, discoveries, failures, and progress cleanup to the desktop system tray
or Android notification channels. Shared Java schedules work while the product
is alive; Android additionally uses an inexact platform alarm to reopen the
product after process death, consult the same durable due time, execute the same
service, and close the graph. The shared Compose Updates screen groups events by
local date and delegates selection, read state, removal, exceptions, and exact
download actions through the owning Java presentations.

ApplicationUpdate is a separate removable vertical because checking the Anilib
application release channel is not library-content update behavior. Its shared
Java service performs a bounded request to the configured GitHub Releases API,
parses only the stable release identity and HTTPS release page, and compares
numeric semantic versions without a third-party library. The Bundle publishes
one immutable presentation consumed by the shared About screen. Android and
desktop therefore expose the same manual check and release-page hand-off while
their launchers supply only the current packaged version and platform identity.

HTTP is split at the platform boundary. Framework contracts own immutable
requests and responses plus cookie, cache, rate-limit, and low-level transport
ports. One shared policy engine applies those contracts. Desktop injects the
Java 21 HTTP/2 transport; Android injects its native URL-connection transport.
The Network Bundle publishes the resulting capabilities, so source code never
imports either platform mechanism and configurations still select one explicit
composition unit.

Network policy is persisted beside the shared cache and read for every request.
The shared client owns default User-Agent and cache admission; a transport
decorator owns timeout caps, HTTP proxy routing, and an RFC 8484 DNS-over-HTTPS
resolution gate before the selected platform transport runs. Diagnostics use
the same configured client and retain a bounded, source-identified in-memory
history, so their result describes the path real source requests take.

Settings is a removable vertical rather than platform-owned preferences. Its
Java service atomically stores immutable snapshots and publishes observations
through a narrow capability. One platform-neutral presentation owns mutation
actions; the shared Compose shell observes it to apply language packs, theme
mode/family/accent palettes, typography scale, and adaptive or forced navigation
immediately and select the persisted initial destination on Android and desktop.
Library remains the owner of category defaults and per-category exceptions. The
searchable Settings home routes into dedicated shared pages;
platform launchers still own no preference behavior. Feature policy values
remain owned here as user choices. Downloads and library updates combine their Wi-Fi
preferences with a platform-supplied network status; Reader and Player suppress
history and progress writes in incognito mode; and Extension Repository hides,
rejects, and skips adult packages unless enabled. Desktop treats its ordinary
host connection as suitable for large transfers, while Android admits validated
Wi-Fi and Ethernet transports.

Database cleanup is coordinated by Settings without taking ownership of feature
state. Downloads, Player, Tracker, and Updates register narrow cleanup callbacks
during Bundle installation and unregister them during shutdown. One confirmed
Android/desktop action removes only records whose Library title no longer exists,
including downloaded files, while every feature performs its own atomic rewrite.
Unlike Aniyomi, Anilib does not persist titles merely viewed during discovery, so
there is no non-library catalogue table to purge.

Settings also owns operational diagnostics without reading feature file formats.
It measures top-level application storage without following links, retains
bounded application logs and uncaught-crash reports, and exports only a summary
plus those reports. Reset is a two-phase plan/confirmation protocol whose exact
targets are allowlisted to settings, HTTP cache, logs, and crash reports; it can
never select the data root, library, downloads, backup, or extension content.

## Product lifecycle

1. A configuration selects feature Bundles.
2. A platform adds only platform-owned plugins or host adapters.
3. The kernel validates and starts one immutable graph.
4. The platform resolves narrow capabilities and renders them.
5. Closing the product releases all plugin sessions in reverse order.

Desktop and Android render the same shared Java presentation models through one
adaptive Compose Multiplatform and Material 3 shell. Each product has a thin
launcher for its own lifecycle, window, storage directory, and final HTTP
transport. Kotlin, Android, and UI toolkit types stay in platform modules; all
inward modules remain ordinary Java and are shared unchanged.

Desktop release packaging is host-native and repeatable rather than
cross-compiled. One fixed workflow matrix builds MSI, DEB, and DMG installers on
their matching operating systems with the same pinned JDK, numeric product
version, complete runtime-module policy, stable platform identifiers, and
dependency graph. Gradle archives normalize timestamps and ordering, dynamic or
changing dependencies fail resolution, and every host publishes deterministic
SHA-256 metadata with its package. AnilibJava owns the repository rule that
keeps this release contract and its three target hosts present.

Tagged application releases compose the desktop matrix with the Android job
before publication. Production mode refuses missing credentials, signs and
timestamp-verifies the Windows MSI with Authenticode, and signs, notarizes, and
staples the macOS DMG through the Compose packaging tasks. Android rejects an
unsigned APK and verifies its signing certificate with pinned SDK tooling.
Linux and every native artifact receive a signed GitHub/Sigstore provenance
attestation. Publication rechecks each host-owned SHA-256 manifest, assigns
unique checksum names, verifies the existing semantic-version tag, and creates
one GitHub Release only after all four platform jobs succeed. Secrets remain in
the GitHub environment and temporary runner key stores are removed after use.

Android release packaging compiles the same Standard product and shared Compose
surface into one versioned APK. The Android adapter owns only application
lifecycle, notifications, alarms, encoded-image decoding, HTTP transport, and
the allowlisted UI/media integrations; feature behavior remains in Java modules
with no Android imports. Release keys enter only through environment variables,
so an unconfigured local or manual build produces an explicit unsigned APK
instead of creating or committing credentials. Tagged publication requires
those secrets. A fixed Linux workflow installs the pinned Android platform,
runs the complete gate, packages the APK, verifies production signatures, and
publishes a SHA-256 manifest. AnilibJava keeps the SDK, platform boundary,
network policy, signing seam, checksum task, and workflow versions present.

The Android extension adapter can also inventory separately installed Aniyomi
APKs that Android already makes visible to Anilib. It reads their feature,
entrypoint/factory, library-version, content, documentation, torrent, and
signing-certificate metadata without loading their classes. Anilib deliberately
does not request unrestricted package visibility. A metadata-compatible result
is therefore discovery evidence, not an execution claim. Users must separately
confirm the package's complete SHA-256 signing-certificate fingerprint; trust is
stored per package and ceases to match when its current signer changes. Only
then does a non-initializing preflight check the Aniyomi, RxJava, HTTP, parser,
injection, coroutine, serialization, preference, and optional torrent host ABI.
AnilibJava keeps that certificate binding and `Class.forName(..., false, ...)`
contract present. At Android startup, ABI-ready entrypoints are constructed with
an APK class loader and reflected into ordinary Source Bundles before the Kernel
graph freezes. The adapter translates catalogue pages, episodes, streams,
headers, and subtitles while retaining activation failures per package.
Configurable-source AndroidX switch, text, and select controls become the same
platform-neutral preference schema rendered for portable sources on desktop.
Discovery owns the durable selection, and the Android boundary mirrors each
validated request snapshot into the APK source's expected `SharedPreferences`
immediately before invoking it. Its
`TRUSTED_PLATFORM_RUNTIME` permission is reserved to this audited platform path
and forbidden to portable extension modules. Every bridged operation rechecks
the installed signer against current trust before invoking APK code. The app
still needs a complete, compatible host ABI before a real installed APK can
reach that Android path.

Desktop supplies the missing ecosystem ABI only through an optional external
JVM sidecar. Selecting an APK install explicitly authorizes Anilib to download
the pinned Apache-2.0 Miwayomi release from its official GitHub asset; the
platform adapter bounds its size, verifies its exact SHA-256, rejects links,
persists the verified selection, and launches a disposable copy with a reduced environment on
`127.0.0.1`, waits for a typed health response, synchronizes user-owned repository
URLs, and owns process shutdown. Extension Repository Runtime maps the loopback
protocol into ordinary explicit Source Bundles with the original unsigned
numeric source identity. Catalogue, chapters, pages, episodes, videos, subtitles,
HLS, and DASH are relayed through that boundary; neither engine classes nor APK
bytecode enter Anilib's classpath. The desktop adapter converts every discovered
source into an explicit leaf Bundle, so an APK installed during a session can be
registered immediately without changing the product capability graph. Portable Anilib Bundles
remain the preferred signed, dependency-free cross-platform format.

The official sidecar checksum is verified before Anilib prepares its disposable
runtime copy. A narrow desktop-owned compatibility step then aligns the sidecar's
QuickJs close contract with current anime extensions. Windows ARM64 additionally
selects GraalJS's pure-Java fallback because the pinned sidecar publishes native
Truffle resources for Windows x64 only. The downloaded artifact and installed APK
remain unchanged, while the temporary runtime works on both Windows architectures.

Anilib deliberately does not synthesize `eu.kanade.*` host classes or copy the
Aniyomi dependency graph into its own process merely to make arbitrary APK bytecode link. APKs whose
preflight finds a missing host ABI remain disabled with a visible report. This
keeps Android compatibility optional and prevents it from becoming a hidden
dependency of the portable Source SDK used by both products.

## External dependency policy

Foundation, Framework, Kernel, Features, Configurations, Tooling, and tests may
use only JDK modules and other Anilib modules. Platform UI adapters may use a
small exact allowlist of audited UI libraries and compiler plugins. AnilibJava
checks both the coordinates and the owning build file; a platform cannot add an
arbitrary dependency merely because it renders UI.

Java modules remain JPMS-enforced. Compose platform applications execute on an
isolated classpath boundary because Compose and AndroidX publish overlapping
automatic module names. Their Anilib dependencies remain explicit in
`module.properties`, and Kotlin source packages, imports, layout, and formatting
are checked by AnilibJava.
