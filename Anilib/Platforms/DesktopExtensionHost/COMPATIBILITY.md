# Desktop extension compatibility

Aniyomi runs Android extension APKs against its Android runtime and source API.
Anilib converts the APK bytecode, relocates Android/Aniyomi references and supplies
Java adapters. Installing an APK alone does not prove that its runtime requirements
are available.

## Supported contract

`src/test/resources/aniyomi-api-v16.api` records 527 public/protected symbols from
the official compiled v16 API. The normal `desktopExtensionHostSmoke` and `check`
tasks verify this entire contract using the production relocator and ABI checker.
Method descriptors include return types, static/instance use and Kotlin synthetic
constructors/default methods. The reference AAR is a compilation stub, so matching
its signatures is only the first gate; behavior is tested separately.

The adapters cover old Rx sources and suspend sources, Hoster/Video model versions,
Kotlin Duration bridges, cancellable HTTP requests, ordered coroutine helpers,
Android URI encoding and LRU caching. Media metadata uses the packaged ffprobe.
Parsed sources are checked through their actual selector/element hooks.

The v17 contract is retained for forward auditing. It is **not a claim of complete
v17 support**. The preflight checks each extension's actual references. API versions
above 17 and torrent sources are rejected explicitly. Torrent streaming, the v17
HTTP server and thumbnail APIs need separate implementations and behavior tests.
Website changes, Cloudflare challenges, geographic restrictions and unavailable
hosts can still prevent a compatible extension from returning content.

## Browser and JavaScript

The desktop adapter explicitly injects Chromium sessions through
`ExtensionPlatformServices`. WebView executes the real page and intercepts actual
resource requests; QuickJs/JavaScriptEngine evaluate JavaScript in persistent
sessions. Sessions have bounded lifetimes and pending evaluations fail when the
renderer terminates. The standalone headless host retains the limited static
media/Anime-Sama array fallback; it is not a general JavaScript engine.

KCEF `2025.03.23` is paired with its documented native release
`jbr-release-17.0.14b1367.22`. Never download an unpinned latest native release with
older Java bindings. Resource, locale and subprocess paths must point to the
installed Chromium directory, rather than the application's `java.home`.
Runtime/cache folders are versioned; old browser data is left intact.

The opt-in native smoke test uses a local HTTP page and an isolated temporary
profile. It checks DOM evaluation, JavaScript-created requests, Unicode and state
across evaluations:

```powershell
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:Desktop:desktopExtensionBrowserSmoke `
  '-Panilib.browserRuntime=C:/path/to/isolated/runtime'
```

## Installation and recovery

An update is converted, checked and instantiated in staging before the installed
pair changes. The runtime write lock covers closing old class loaders and
committing the replacement. A journal backs up the APK/JAR pair; failures roll
back and startup recovers interrupted transactions. A rejected update keeps the
installed version. Error responses expose a diagnostic ID and preserve the cause
in the host log. Generated conversion format 3 replaces the earlier linear
constructor repair with receiver dataflow analysis.

## Updating the reference

Reference artifacts are verification inputs only, never runtime dependencies.
Download and inspect the official AAR, verify its hash, extract `classes.jar`, then
generate a new contract. Keep the previous supported contract until the new one
passes both binary and behavior tests. Do not add no-op APIs to satisfy the gate.

| Version | Official AAR SHA-256 | classes.jar SHA-256 |
| --- | --- | --- |
| v16 | `f2f8e00765ccea076bd3eb2e657456177d10846699690922ea11d8271a9c65f0` | `cf981398207aaf4249784ed3772e426dd0a9de27f3ca83d6851e6f45a8a047c0` |
| v17 | `6912054943d9c88f9885c8bb579d17a7c4b71cedf437b95986ebb13fdd180de2` | `7b4f6d648f556b337ab7b8a7c0e2ea22b64f693c40a9e395e701f42323c6d86a` |

```powershell
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:DesktopExtensionHost:officialExtensionApi `
  '-Panilib.apiReference=build/api-reference/classes.jar' `
  '-Panilib.apiContract=src/test/resources/aniyomi-api-v16.api'

# Audit a future contract without changing the supported v16 gate.
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:DesktopExtensionHost:officialExtensionApi `
  '-Panilib.apiContract=src/test/resources/aniyomi-api-v17.api'
```

For a real APK corpus, use a directory containing APKs and optionally existing
converted JARs. All conversions and operations use temporary copies. Loading is a
hard gate; optional live catalogue checks report external failures separately.
Live checks are not proof of episode resolution, download or playback support.

```powershell
.\gradlew.bat --no-daemon --console=plain :Anilib:Platforms:DesktopExtensionHost:desktopExtensionCorpusSmoke `
  '-Panilib.extensionCorpus=C:/path/to/fixtures' '-Panilib.liveExtensionCorpus=true'
```

The pinned `desktopExtensionCompatibilitySmoke` additionally exercises catalogue,
details, episodes/videos and manga pages with the fixture pins required by its
Gradle properties. Default offline checks require no external website.

## Upstream references

- [Official extension API and stub limitations](https://github.com/aniyomiorg/extensions-lib)
- [Aniyomi source API behavior](https://github.com/aniyomiorg/aniyomi/tree/main/source-api)
- [Aniyomi HTTP/coroutine implementation](https://github.com/aniyomiorg/aniyomi/tree/main/core/src/main/java/eu/kanade/tachiyomi/network)
- [KCEF release and compatible native versions](https://github.com/DatL4g/KCEF/releases)
- [v16 reference AAR](https://jitpack.io/com/github/aniyomiorg/extensions-lib/v16/extensions-lib-v16.aar)
- [v17 reference AAR](https://jitpack.io/com/github/aniyomiorg/extensions-lib/v17/extensions-lib-v17.aar)
