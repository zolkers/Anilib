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

The classic `apk` remains Android-only. The `anilib` Bundle is the portable
artifact used by Android and desktop after checksum, signature, compatibility,
and installation validation. Repository discovery and artifact installation
are separate capabilities so merely viewing an index never executes code.

Portable artifacts are accepted only when `sha256`, `signature`, `keyId`, and
`api` are present, the user has explicitly imported the publisher's X.509
Ed25519 public key, and the raw archive verifies against both checksum and
signature. The ZIP/JAR must contain a bounded
`META-INF/anilib-extension.properties` entry whose `package`, `versionCode`,
and `api` exactly match the signed repository metadata. Installation state and
artifacts are written atomically; viewing a catalogue never downloads them.

The current lifecycle stores and manages verified artifacts. Loading enabled
artifact code into the Source registry is a separate roadmap item because it
must preserve the single explicit Bundle graph and restart isolation.
