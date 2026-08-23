# Extension repository feature

This removable vertical owns user-supplied source repository URLs. Standard
starts it with an empty list: Anilib does not ship, recommend, or silently add a
third-party catalogue.

The runtime detects and accepts both the Aniyomi legacy
`index.json`/`index.min.json` shape and the Mihon v2 `index.pb` Protobuf shape.
The Protobuf decoder is a bounded, dependency-free implementation of the
published Mihon field numbers and retains package, version, content warning,
APK, icon, source identity, language, and home-URL metadata. Index and artifact
downloads require HTTPS, gzip expansion is bounded, redirects are revalidated
hop by hop, duplicate packages are rejected, and URLs with credentials or
fragments are invalid.

Offline compatibility tests use synthetic entries matching the public
[Yuzono anime](https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json)
JSON shape and [Keiyoushi manga](https://github.com/keiyoushi/extensions)
Mihon Protobuf shape inspected on 2026-08-19. They retain only field structure
and edge cases such as unsigned 64-bit source identifiers, `all`/regional
languages, and filename-only APK paths; no third-party catalogue entry is
redistributed. If a configured legacy JSON URL contains only Keiyoushi's app
migration notices, refresh continues with the adjacent `index.pb` catalogue.
Filename-only APK values resolve through the conventional adjacent `apk/`
directory, while explicit relative paths and absolute HTTPS URLs remain intact.

`pkg` is an opaque publisher identity: no `eu.kanade`, reverse-domain, vendor,
or Java-package prefix is required. Anilib preserves printable Unicode values
verbatim and derives separate hashed local filenames, so accepting another
ecosystem's identity cannot escape the managed extension directory.

Users may paste either a direct HTTPS index URL or a GitHub repository URL such
as `https://github.com/publisher/anilib-sources`. A GitHub repository is resolved
dynamically through its default branch, trying `index.pb`, `index.min.json`, and
`index.json`, then through the conventional publication branch `repo`. Relative
Bundle URLs remain relative to the fetched raw index.

The Desktop repository screen stores its language selection and
pinned package identities beside the repository list. An empty language
selection means that all currently available languages are shown, so newly
published languages are visible by default. At least one available language
must remain enabled, and pinned packages are ordered before the rest without
changing repository metadata.

The catalogue always lists every package published by the configured indexes,
including packages carrying an adult-content warning. The shared screen marks
those entries as `18+`; while the adult-content setting is disabled they remain
searchable and inspectable, but their installation actions stay disabled. This
keeps repository discovery complete without bypassing the user's content
policy. Platform lists remain lazy, so a catalogue containing thousands of
entries does not eagerly create every card.

Installed portable Bundles and extension APKs are grouped ahead of the
available catalogue and can be searched by extension, package, source, or
language. Removal always requires confirmation. Portable artifacts are deleted
from Anilib's managed store, while Desktop uses Anilib's embedded extension-host
uninstall endpoint for APKs. Dynamic
sources are detached immediately; sources selected during startup disappear on
the next restart. Removing a repository is a separate confirmed action and
keeps extensions already installed from it.

The UI makes artifact support explicit. Desktop exposes `Install for desktop`
through its bundled Anilib host; a compatible APK activates its new Source Bundles
immediately, while an APK that yields no executable source is rolled back and
reported as failed. Signed portable Anilib Bundles keep
the ordinary `Install` action. Pinning updates the
filled/outlined icon and catalogue ordering immediately, and an empty Browse
extension tab links directly to repository management.

One entry may additionally advertise a portable artifact without breaking an
Aniyomi client:

```json
{
  "name": "Example",
  "pkg": "org.example.extension",
  "apk": "example.apk",
  "lang": "en",
  "code": 2,
  "version": "1.2",
  "nsfw": 0,
  "anilib": {
    "bundle": "example.jar",
    "api": "1.4",
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "signature": "BASE64_SIGNATURE",
    "keyId": "publisher-key",
    "kind": "manga"
  },
  "sources": [
    {"name": "Example", "lang": "en", "id": "42", "baseUrl": "https://example.org"}
  ]
}
```

The `apk` artifact runs on Desktop through the isolated AniYomi/Mihon
compatibility host. The `anilib` Bundle is the preferred native artifact after
checksum, signature, compatibility, and installation validation. Repository
discovery and artifact installation are separate capabilities so merely viewing
an index never executes code. When both exist, the availability model selects
the native Bundle and retains the APK as repository metadata.

`Tooling/SourcePublisher` accepts an optional `apk=<local-file.apk>` in a package
configuration. It copies that fallback under `apk/`, emits one entry for the
shared `pkg`, signs the Bundle, and writes `index.json`, `index.min.json`, and
`checksums.sha256`. The reusable `source-repository-publish.yml` workflow wraps
that publication flow for external repositories while keeping the publisher's
private Ed25519 key in the repository secret.

The active extension system belongs to Anilib: shared contracts use artifact-
specific APK and portable Bundle terminology. `Aniyomi` names are restricted to
the repository-shape and Desktop host-ABI compatibility adapters.

## Desktop APK extension discovery

On Desktop, the repository screen also lists AniYomi and Mihon-compatible APKs
installed in Anilib's managed extension store. The compatibility host recognizes the
`tachiyomi.animeextension` and `tachiyomi.extension` features,
projects entrypoint/factory, library, content, documentation, torrent, and
SHA-256 signing-certificate metadata into a Java UI contract, and labels malformed
or unsupported packages. Anime and manga packages receive separate library-version
and host-class preflights. Discovery never loads extension bytecode.

Metadata compatibility does not grant trust. The shared screen displays the
complete current certificate fingerprint in a confirmation dialog before the
Desktop host stores a package-specific decision. A signer change invalidates
that decision. Trusted packages receive a non-initializing host-ABI preflight;
missing Aniyomi, RxJava, OkHttp, jsoup, Injekt, NanoHTTPD, coroutine,
serialization, preference, and torrent classes remain visible as a blocked
runtime state. Forgetting trust is immediate.

Discovery alone is metadata compatibility only. Existing Aniyomi APKs compile against the
Aniyomi source API and host-provided external libraries, so they are not executed
until the Desktop compatibility runtime can satisfy that ABI. When preflight is
green, Desktop constructs the declared source or source factory before
Standard startup and adapts catalogue pages, episodes, streams, request headers,
and subtitles into explicit Anilib Source Bundles. The reflection bridge accepts
both ext-lib 16 RxJava calls and ext-lib 17 suspend catalogue, combined episode
update, hoster, and video calls. Text, checkbox, tri-state, select, sort, and
grouped extension filters are projected into the shared discovery model and
written back to a fresh ABI filter list for each search. Configurable anime
sources project AndroidX switch, text, and select controls into the same Source
preference schema used by portable Bundles. Anilib's Desktop screen owns the
durable selection; the compatibility host commits its request
snapshot into the APK source's expected `SharedPreferences` immediately before
the source call. Metadata-compatible manga APKs use a separate adapter for
RxJava or suspend catalogue/search calls, combined manga/chapter updates,
chapter pages, bounded page downloads through the extension HTTP client,
filters, preferences, and the Anilib Reader contract. One package failure does not
select a partial Bundle and is displayed in the shared repository screen. Each
bridged operation rechecks the currently installed signer, so forgetting trust
or replacing the APK immediately blocks subsequent calls.
Signed portable Anilib Bundles remain the preferred source artifact executed on
Desktop without that compatibility ABI.

Portable artifacts are accepted only when `sha256`, `signature`, `keyId`, and
`api` are present, the user has explicitly imported the publisher's X.509
Ed25519 public key, and the raw archive verifies against both checksum and
signature. The ZIP/JAR must contain a bounded
`META-INF/anilib-extension.properties` entry whose `package`, `versionCode`,
and `api` exactly match the signed repository metadata. It also declares one
explicit JPMS module and one or more Source factories; no classpath or service
scan is performed:

```properties
package=org.example.extension
versionCode=2
api=1.4
module=org.example.extension
source.count=1
source.0.id=example.manga
source.0.component=extension.example.manga
source.0.name=Example Manga
source.0.factory=org.example.extension.ExampleSourceFactory
source.0.origins=https://example.org,https://cdn.example.org
```

The module must be explicit and closed, export the factory package, require
only `java.base` and `fr.vriege.anilib.feature.source.api`, and declare neither
service discovery nor multi-release classes. Every factory implements
`SourceExtensionFactory` with a public no-argument constructor. An empty
`origins` value creates an offline Source; exact HTTP(S) origins determine its
network permissions.

Installation state and artifacts are written atomically; viewing a catalogue
never downloads them. At product startup, Standard selects enabled artifacts,
rechecks their checksum and descriptor, loads each archive from bounded memory
into its own child module layer, and adds the resulting Source Bundles to the
single Kernel graph. Disabled artifacts wait for a later restart. One corrupt
or incompatible archive is skipped and exposed through
`ExtensionInstallationService.loadFailures()` without preventing valid Bundles
or the product from starting. JPMS and the restricted Source context are
dependency/capability boundaries, not a security sandbox for an untrusted
publisher.

## Update channel

The Desktop screen exposes available updates, a verified
`Update all` action, and an opt-in automatic channel. Automatic checks run every
six hours and update only when `pkg` is unchanged, the version code increases,
the artifact is portable, and its Ed25519 `keyId` exactly matches the publisher
recorded at installation. Every update still revalidates HTTPS redirects,
SHA-256, signature, Source API compatibility, and the embedded descriptor.
Updates take effect after restart so the active Kernel graph remains immutable.
