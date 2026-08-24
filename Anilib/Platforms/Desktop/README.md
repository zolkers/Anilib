# Desktop release

## Existing APK sources

Desktop runs existing manga and anime extension APKs through Anilib's bundled
`DesktopExtensionHost`. It needs no separately downloaded engine and no
`engine.properties`. The host starts on an ephemeral loopback port, converts
each APK into an isolated JVM archive, supplies the required Android/Aniyomi ABI,
and registers every discovered source as an explicit Source Bundle. Catalogue,
pages, episodes, subtitles, HLS and DASH traffic stays behind that boundary.
APKs installed while Anilib is open activate immediately.

Existing APK files under `extension-engine/data/extensions` are detected and
converted automatically. A package failure is returned as a recoverable host
error and cannot terminate the desktop application. The opt-in
`desktopExtensionCompatibilitySmoke` task installs and queries real manga and
anime APKs when their paths are supplied as Gradle properties.

Anilib produces self-contained desktop packages through the Compose
Multiplatform `jpackage` integration:

The desktop Player negotiates advanced commands with its selected native
backend. The bundled backend currently advertises loop and restart; compatible
backends may additionally expose frame stepping, audio/subtitle delay, aspect
ratio, and deinterlacing through the same platform-neutral contract.

- `Anilib-<version>.msi` on Windows;
- `anilib_<version>-1_amd64.deb` on Linux;
- `Anilib-<version>.dmg` on macOS.

Application updates download only the installer named and hashed by the signed
release manifest. After shared verification, Desktop opens the MSI, DEB, or
notarized DMG with the operating system's registered installer. Elevation,
package policy, Gatekeeper, and final confirmation remain platform-owned.

Cross-compilation is deliberately not attempted because the native packaging
toolchain requires its target operating system. The
`.github/workflows/desktop-release.yml` matrix uses fixed Windows, Ubuntu, and
macOS runner generations with Microsoft OpenJDK 21.0.10. A manual version drives
the reusable packaging workflow; a numeric `vMAJOR.MINOR.PATCH` tag invokes it
through `application-release.yml` with production signing required.

Run the current-host pipeline locally with:

```powershell
.\gradlew.bat --no-daemon --console=plain `
  :Anilib:Platforms:Desktop:stageDesktopRelease `
  '-PanilibVersion=1.0.2' '-PanilibPackageVersion=1.0.2'
```

The build rejects dynamic or changing dependencies, normalizes every Gradle
archive's timestamps and entry order, validates the native numeric version,
uses stable application identifiers, and includes the complete Java runtime
module set. Every host publishes its installer and `SHA256SUMS` in the visible
`release/` directory at the repository root, then removes the module's package
copy.
AnilibJava verifies that the three target formats, pinned runner matrix,
toolchain, integrity manifest, and stable identifiers remain present.

Unsigned development packages are suitable for local testing. Production uses
these GitHub secrets:

- `ANILIB_WINDOWS_CERTIFICATE_BASE64` and
  `ANILIB_WINDOWS_CERTIFICATE_PASSWORD` for the Authenticode PFX;
- `ANILIB_MACOS_CERTIFICATE_BASE64`,
  `ANILIB_MACOS_CERTIFICATE_PASSWORD`, and
  `ANILIB_MACOS_SIGNING_IDENTITY` for the Developer ID P12;
- `ANILIB_MACOS_NOTARIZATION_APPLE_ID`,
  `ANILIB_MACOS_NOTARIZATION_PASSWORD`, and
  `ANILIB_MACOS_NOTARIZATION_TEAM_ID` for Apple notarization.

The Windows runner signs, timestamps, and verifies the MSI. The macOS runner
signs the application, notarizes the DMG, staples the ticket, and verifies the
result. Temporary certificates and keychains are removed even after failure.
The final publication job verifies all platform checksums and adds a signed
GitHub/Sigstore provenance attestation before creating one GitHub Release.

Backup import and export use native AWT file dialogs. Sharing puts the selected
archive on the operating-system file clipboard without changing the managed
backup copy.

The desktop-hosted Compose acceptance test renders fixed 480x720 compact and
1000x720 expanded surfaces, clicks through the shared navigation routes, and
compares consecutive pixel buffers and semantics trees. Run it with:

```powershell
.\gradlew.bat --no-daemon --console=plain `
  :Anilib:Platforms:Desktop:test `
  --tests fr.vriege.anilib.platform.desktop.UiRouteScreenshotTest
```
