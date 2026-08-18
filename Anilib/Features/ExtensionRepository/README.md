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
