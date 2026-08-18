# Extension repository feature

This removable vertical owns user-supplied source repository URLs. Standard
starts it with an empty list: Anilib does not ship, recommend, or silently add a
third-party catalogue.

The runtime accepts the Aniyomi `index.json`/`index.min.json` shape, including
`name`, `pkg`, `apk`, `lang`, `code`, `version`, `nsfw`, and `sources`. Index and
artifact downloads require HTTPS, redirects are revalidated hop by hop, index
size and nesting are bounded, duplicate packages are rejected, and URLs with
credentials or fragments are invalid.

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

The `apk` artifact remains Android-only. The `anilib` Bundle is the portable
artifact used by Android and desktop after checksum, signature, compatibility,
and installation validation. Repository discovery and artifact installation
are separate capabilities so merely viewing an index never executes code.

The active extension system belongs to Anilib: shared contracts use artifact-
specific APK and portable Bundle terminology. `Aniyomi` names are restricted to
the repository-shape and Android host-ABI compatibility adapters.

## Android APK extension discovery

On Android, the shared repository screen also lists separately installed
Aniyomi extension APKs that are visible under Android's normal package-visibility
rules. The platform adapter recognizes the `tachiyomi.animeextension` feature,
projects entrypoint/factory, library, content, documentation, torrent, and
SHA-256 signing-certificate metadata into a Java UI contract, and labels malformed
or unsupported packages. Discovery never loads extension bytecode and Anilib does
not request `QUERY_ALL_PACKAGES`.

Metadata compatibility does not grant trust. The shared screen displays the
complete current certificate fingerprint in a confirmation dialog before the
Android adapter stores a package-specific decision. A signer change invalidates
that decision. Trusted packages receive a non-initializing host-ABI preflight;
missing Aniyomi, RxJava, OkHttp, jsoup, Injekt, NanoHTTPD, coroutine,
serialization, preference, and torrent classes remain visible as a blocked
runtime state. Forgetting trust is immediate.

This is metadata compatibility only. Existing Aniyomi APKs compile against the
Aniyomi source API and host-provided external libraries, so they are not executed
until a separately isolated Android compatibility runtime can satisfy that ABI.
Signed portable Anilib Bundles remain the only source artifact executed on both
Android and desktop.

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
