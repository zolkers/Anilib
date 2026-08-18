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
The graph becomes immutable after startup.

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
boundary for product-selected modules; arbitrary untrusted bytecode is not
loaded or claimed to be safely sandboxed.

Discovery is a separate vertical feature over the Source registry and Library
catalog. Its Java service owns paging, global and per-source search, validated
filters, durable source preferences, duplicate-safe library admission, and
migration while retaining user-owned progress, history, categories, and
favourites. Its platform-neutral presentation is the only surface consumed by
the shared Compose Browse screen, so Android and desktop cannot drift into two
different discovery implementations.

Reader is another removable vertical over Library and Source. Its Bundle
resolves a library origin only through the typed Source registry and accepts
only sources implementing the versioned `PagedSource` contract. The shared
runtime owns validated sessions, durable progress and history, asynchronous
neighbor prefetch, defensive byte ownership, and a bounded LRU page cache.
Reading directions and controller state stay platform-neutral; Compose renders
one immersive screen on Android and desktop, while each outer adapter performs
only its native encoded-image decoding.

Downloads is a removable vertical over Library, Source, and Reader. Its Bundle
owns the durable job queue, atomic metadata writes, page files, concurrent-job
limit, per-page and total-storage policies, pause/cancel/resume transitions, and
restart reconciliation of partial jobs. It registers one typed content provider
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

HTTP is split at the platform boundary. Framework contracts own immutable
requests and responses plus cookie, cache, rate-limit, and low-level transport
ports. One shared policy engine applies those contracts. Desktop injects the
Java 21 HTTP/2 transport; Android injects its native URL-connection transport.
The Network Bundle publishes the resulting capabilities, so source code never
imports either platform mechanism and configurations still select one explicit
composition unit.

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
