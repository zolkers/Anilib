# Desktop extension host: title-details compatibility gaps

## Purpose

This document records the failures observed after replacing the transitional
external compatibility process with Anilib's embedded `DesktopExtensionHost`.
It is intended to give a contributor with no prior project context enough
information to reproduce, understand, and fix the problem without reintroducing
a dependency on Miwayomi.

The failures concern desktop execution of existing Aniyomi/Mihon-compatible APK
extensions. Repository parsing, APK installation, source discovery, and initial
catalogue browsing are operational. The broken boundary begins when a catalogue
item is opened and Anilib asks the extension for its full title data.

## User-visible failures

### Anime

Opening an Anime-Sama title successfully displays catalogue metadata, but the
episode section fails with:

```text
Extension engine request failed with HTTP 500: {"error":"UnsupportedOperationException"}
```

The extension is installed and its catalogue is reachable. The missing episode
list is therefore not caused by a missing repository, a failed download, or a
source-registration problem.

### Manga

Opening a manga title such as `Solo Leveling` produces an almost empty detail
screen followed by:

```text
Extension engine request failed with HTTP 500: {"error":"NullPointerException"}
```

The same broad transition is affected: catalogue data exists, but the request
for details or the first dependent content operation fails inside the desktop
extension boundary.

### Scope

These observations apply to the embedded desktop host. Android uses its native
APK loading boundary and must be tested separately. The symptoms are not proof
of corrupted library data, and uninstalling extensions or clearing the library
is not an appropriate workaround.

## Request path

The failing data follows this path:

1. Compose opens a catalogue item.
2. the platform-neutral Source feature requests details and content units;
3. `DesktopExtensionSourceBridge` sends a loopback request to the embedded host;
4. `DesktopExtensionHostServer` selects the manga or anime operation;
5. `ExtensionSourceOperations` loads the APK source and invokes its ABI methods;
6. the converted extension calls the compatibility classes owned by
   `Platforms/DesktopExtensionHost`;
7. a runtime exception is reduced to a generic HTTP 500 response;
8. the UI retains too little catalogue state to render a useful partial detail
   page, so the failure appears as an empty screen or a missing episode list.

The relevant endpoints are:

```text
GET /api/v1/anime/{sourceId}/details?url=...
GET /api/v1/anime/{sourceId}/episodes?url=...
GET /api/v1/anime/{sourceId}/videos?url=...
GET /api/v1/manga/{sourceId}/details?url=...
GET /api/v1/manga/{sourceId}/chapters?url=...
GET /api/v1/manga/{sourceId}/pages?url=...
```

## Confirmed design mismatch

`ExtensionSourceOperations` currently invokes the classic `HttpSource`
request/parse pairs directly for most operations:

```text
animeDetailsRequest -> animeDetailsParse
episodeListRequest  -> episodeListParse
mangaDetailsRequest -> mangaDetailsParse
chapterListRequest  -> chapterListParse
```

That is only one generation of the Aniyomi source ABI. Current extensions may
instead implement higher-level public or suspend operations, hoster flows, or
helper APIs generated from Kotlin. Calling the inherited classic method can
therefore reach a compatibility placeholder even though the extension has a
working implementation through another ABI entry point. The anime error is
consistent with that mismatch: a placeholder or unsupported path is being
selected instead of the extension's effective episode operation.

The video path already demonstrates the required direction by invoking the
suspend `getVideoList` operation. Details, episodes, chapters, and pages do not
yet use an equivalent capability-aware dispatcher.

## Manga failure: confirmed resource-loading mismatch

The complete causal chain identifies the null value. MangaDex creates its
translation bundle through `ClassLoader.getResourceAsStream` and expects files
such as `assets/i18n/messages_en.properties` to remain reachable from the
installed APK. The desktop loader previously exposed only the DEX-to-JAR output,
which contains converted classes but not the APK assets. The missing stream was
then passed to `InputStreamReader`, producing the observed
`NullPointerException` during `mangaDetailsParse`.

Aniyomi keeps the original APK as the extension's code/resource path. The
desktop host now mirrors that boundary: converted classes remain first in the
isolated child-first loader, while the original installed APK is retained as a
second loader URL for resources. A network-free regression test loads a
synthetic `assets/i18n/messages_en.properties` file through an extension class
to prevent this failure from returning.

## Why the current smoke test did not catch this

The opt-in compatibility smoke test installs MangaDex and Anime-Sama, discovers
their sources, and verifies only an initial manga catalogue and anime search.
Those checks prove APK conversion, class loading, networking, parsing, and
catalogue projection, but they stop before the failing operations.

A green catalogue smoke test must not be described as full extension parity.
The test needs to follow a real item through the whole media-specific workflow.

## Required correction

### 1. Make failures diagnosable

- [x] assign a correlation ID to every host operation;
- [x] log the operation, package, source ID, sanitized URL, exception type,
  message, causal chain, and stack trace locally;
- [x] return a stable error code and correlation ID to the application;
- [x] show a localized, useful popup while keeping technical details available
  through diagnostics;
- [x] distinguish unsupported capability, remote HTTP failure, parse failure,
  ABI failure, and internal host failure instead of returning generic HTTP 500
  for all of them.

No cookies, authorization headers, tokens, or full sensitive query strings may
be written to the log.

### 2. Dispatch the effective source API

- [x] detect the operations actually implemented by the loaded source class;
- [x] prefer the current high-level and suspend APIs;
- [x] support modern anime episode/hoster/video flows;
- [x] support modern manga details/chapter/page flows;
- [x] use classic request/parse pairs only when the extension overrides or
  intentionally relies on that API;
- [x] never treat an inherited Anilib compatibility placeholder as an extension
  implementation;
- [x] unwrap Kotlin coroutine and reflection failures without losing their root
  cause.

The dispatcher belongs in the host compatibility layer. Feature and Compose
code must continue to depend only on Anilib Source contracts.

### 3. Preserve the source item across requests

- [x] retain or transmit the complete catalogue model required to construct
  `SAnime` or `SManga`, rather than rebuilding an object from only its URL;
- [x] preserve title, thumbnail, status, description, and extension-specific
  identifiers where the ABI requires them;
- [x] keep the source ID and item ID stable across catalogue, details, units,
  reader, and player requests;
- [x] render the already-known catalogue metadata if a later network operation
  fails, instead of replacing the whole detail page with an empty state.

### 4. Complete the host ABI from evidence

- [x] add only the Android/ANIYOMI/Kotlin helper contracts reached by pinned
  representative extensions;
- [x] implement real behavior for required context, preferences, injection,
  serialization, coroutine, HTTP, cookie, and parser calls;
- [x] reject unsupported packages with an explicit compatibility report instead
  of allowing them to fail after navigation;
- [x] keep all third-party and Android-shaped compatibility code confined to
  `Platforms/DesktopExtensionHost`.

This work must not add `eu.kanade` packages to shared Anilib modules and must not
restore Miwayomi as a runtime dependency.

## Regression suite to add

The test fixtures must be pinned by repository revision, APK version, package,
and SHA-256. Redistribution and live-network execution must remain opt-in when
the upstream license or service requires it.

### Anime workflow

- [x] install the pinned Anime-Sama APK;
- [x] discover the expected source;
- [x] search for a stable title;
- [x] open its details;
- [x] obtain a non-empty episode list;
- [x] obtain hosters or video streams for one episode;
- [x] verify stream headers and subtitles survive projection;
- [x] repeat the request to expose class-loader or state-lifetime bugs.

### Manga workflow

- [x] install the pinned MangaDex APK;
- [x] discover the expected source;
- [x] search for or browse a stable title;
- [x] open its details without a null failure;
- [x] obtain a non-empty chapter list;
- [x] obtain a non-empty ordered page list for one chapter;
- [x] repeat the request to expose class-loader or state-lifetime bugs.

### Product behavior

- [ ] verify both workflows from the packaged desktop application;
- [ ] verify a clean installation and migration of an already installed APK;
- [ ] verify Windows x64 and Windows ARM64;
- [x] verify a failed source operation does not crash or blank the application;
- [ ] verify the same title can still be opened from Browse and Library after a
  retry;
- [x] run `javaQuality`, `architectureTest`, and the complete `check` gate.

## Acceptance criteria

The gap is closed only when all of the following are true:

1. Anime-Sama titles show their real episodes and an episode can reach the
   player flow.
2. MangaDex titles show details and chapters and a chapter can reach the reader
   flow.
3. No generic `UnsupportedOperationException`, `NullPointerException`, or raw
   HTTP 500 text is shown to the user.
4. A source-specific failure leaves a usable detail screen and does not crash
   Anilib.
5. Automated tests cover every operation after catalogue discovery, not only
   the first list response.
6. Desktop continues to use the embedded Anilib host with no Miwayomi runtime
   dependency.

## Primary implementation locations

```text
Anilib/Platforms/DesktopExtensionHost/
  extension/ExtensionSourceOperations.java
  server/DesktopExtensionHostServer.java
  compat/aniyomi/

Anilib/Features/ExtensionRepository/Runtime/
  DesktopExtensionSourceBridge.java

Anilib/Platforms/Compose/
  detail and error presentation

Anilib/Platforms/DesktopExtensionHost/src/test/
  ExtensionCompatibilitySmoke.java
```

## Current status

- [x] repository parsing and APK installation work;
- [x] installed sources are discovered and activated without restart;
- [x] initial Anime-Sama search and MangaDex catalogue requests work;
- [x] effective high-level, suspend, Rx, classic, and hoster dispatch is implemented;
- [x] complete catalogue models survive the desktop bridge;
- [x] full root-cause diagnostics and stable application errors are retained;
- [x] unsupported host ABI symbols are rejected during source discovery;
- [x] extension-owned APK assets are visible to converted extension classes;
- [x] the opt-in regression harness covers details, units, pages, episodes, and videos;
- [ ] Anime-Sama detail-to-episode execution works;
- [ ] MangaDex detail-to-chapter execution works;
- [ ] packaged Windows x64 and Windows ARM64 workflows are manually verified.

The implementation is complete. The remaining unchecked items require opt-in
live-service or hardware verification; they are not claimed by the local,
network-free verification run.
