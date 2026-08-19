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

## Manga failure: what is and is not known

The manga response contains only `NullPointerException`; the server currently
discards the stack trace, causal chain, operation name, extension package, and
source ID. Consequently, the exact null value is not yet proven.

The main candidates to verify are:

- invocation of an inherited compatibility method instead of the extension's
  modern operation;
- creation of an `SManga` containing only its URL when the extension expects
  additional catalogue fields;
- a missing Android context, preference, dependency-injection, serialization,
  coroutine, or networking ABI value;
- conversion of a Kotlin/DEX call site whose nullability or default-argument
  contract is not reproduced by the Java host;
- an extension helper whose required host implementation still returns null.

These are investigation targets, not established root causes. The first fix
must improve diagnostics and capture the complete causal chain before changing
unrelated UI or persistence code.

## Why the current smoke test did not catch this

The opt-in compatibility smoke test installs MangaDex and Anime-Sama, discovers
their sources, and verifies only an initial manga catalogue and anime search.
Those checks prove APK conversion, class loading, networking, parsing, and
catalogue projection, but they stop before the failing operations.

A green catalogue smoke test must not be described as full extension parity.
The test needs to follow a real item through the whole media-specific workflow.

## Required correction

### 1. Make failures diagnosable

- [ ] assign a correlation ID to every host operation;
- [ ] log the operation, package, source ID, sanitized URL, exception type,
  message, causal chain, and stack trace locally;
- [ ] return a stable error code and correlation ID to the application;
- [ ] show a localized, useful popup while keeping technical details available
  through diagnostics;
- [ ] distinguish unsupported capability, remote HTTP failure, parse failure,
  ABI failure, and internal host failure instead of returning generic HTTP 500
  for all of them.

No cookies, authorization headers, tokens, or full sensitive query strings may
be written to the log.

### 2. Dispatch the effective source API

- [ ] detect the operations actually implemented by the loaded source class;
- [ ] prefer the current high-level and suspend APIs;
- [ ] support modern anime episode/hoster/video flows;
- [ ] support modern manga details/chapter/page flows;
- [ ] use classic request/parse pairs only when the extension overrides or
  intentionally relies on that API;
- [ ] never treat an inherited Anilib compatibility placeholder as an extension
  implementation;
- [ ] unwrap Kotlin coroutine and reflection failures without losing their root
  cause.

The dispatcher belongs in the host compatibility layer. Feature and Compose
code must continue to depend only on Anilib Source contracts.

### 3. Preserve the source item across requests

- [ ] retain or transmit the complete catalogue model required to construct
  `SAnime` or `SManga`, rather than rebuilding an object from only its URL;
- [ ] preserve title, thumbnail, status, description, and extension-specific
  identifiers where the ABI requires them;
- [ ] keep the source ID and item ID stable across catalogue, details, units,
  reader, and player requests;
- [ ] render the already-known catalogue metadata if a later network operation
  fails, instead of replacing the whole detail page with an empty state.

### 4. Complete the host ABI from evidence

- [ ] add only the Android/ANIYOMI/Kotlin helper contracts reached by pinned
  representative extensions;
- [ ] implement real behavior for required context, preferences, injection,
  serialization, coroutine, HTTP, cookie, and parser calls;
- [ ] reject unsupported packages with an explicit compatibility report instead
  of allowing them to fail after navigation;
- [ ] keep all third-party and Android-shaped compatibility code confined to
  `Platforms/DesktopExtensionHost`.

This work must not add `eu.kanade` packages to shared Anilib modules and must not
restore Miwayomi as a runtime dependency.

## Regression suite to add

The test fixtures must be pinned by repository revision, APK version, package,
and SHA-256. Redistribution and live-network execution must remain opt-in when
the upstream license or service requires it.

### Anime workflow

- [ ] install the pinned Anime-Sama APK;
- [ ] discover the expected source;
- [ ] search for a stable title;
- [ ] open its details;
- [ ] obtain a non-empty episode list;
- [ ] obtain hosters or video streams for one episode;
- [ ] verify stream headers and subtitles survive projection;
- [ ] repeat the request to expose class-loader or state-lifetime bugs.

### Manga workflow

- [ ] install the pinned MangaDex APK;
- [ ] discover the expected source;
- [ ] search for or browse a stable title;
- [ ] open its details without a null failure;
- [ ] obtain a non-empty chapter list;
- [ ] obtain a non-empty ordered page list for one chapter;
- [ ] repeat the request to expose class-loader or state-lifetime bugs.

### Product behavior

- [ ] verify both workflows from the packaged desktop application;
- [ ] verify a clean installation and migration of an already installed APK;
- [ ] verify Windows x64 and Windows ARM64;
- [ ] verify a failed source operation does not crash or blank the application;
- [ ] verify the same title can still be opened from Browse and Library after a
  retry;
- [ ] run `javaQuality`, `architectureTest`, and the complete `check` gate.

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
- [ ] Anime-Sama detail-to-episode execution works;
- [ ] MangaDex detail-to-chapter execution works;
- [ ] full root-cause diagnostics are retained;
- [ ] the end-to-end compatibility regression suite is complete.

No functional fix is included with this document. It defines the defect and the
work required for the next implementation pass.
