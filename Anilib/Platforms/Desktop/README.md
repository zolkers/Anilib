# Desktop release

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
  :Anilib:Platforms:Desktop:writeDesktopReleaseChecksums `
  '-PanilibVersion=0.1.0' '-PanilibPackageVersion=0.1.0'
```

The build rejects dynamic or changing dependencies, normalizes every Gradle
archive's timestamps and entry order, validates the native numeric version,
uses stable application identifiers, and includes the complete Java runtime
module set. Every host emits `build/release/SHA256SUMS` beside its installer.
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
