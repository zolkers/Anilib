# Android release

The Android product is a thin application adapter over the same Standard
configuration and shared Compose screens used by desktop. Feature state,
behavior, and extension contracts remain Java 21 code outside the Android
module. Android SDK types and the exact audited AndroidX/Compose integrations
stay in the platform boundary.

## Local package

Use JDK 21 and an Android SDK containing platform 37, then run from the
repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain `
  :Anilib:Platforms:Android:writeAndroidReleaseChecksums `
  -PanilibAndroidVersionName=0.1.0 `
  -PanilibAndroidVersionCode=1
```

The task creates the APK under
`Anilib/Platforms/Android/build/outputs/apk/release` and its checksum manifest
under `Anilib/Platforms/Android/build/release/SHA256SUMS`. Both directories are
generated output and are not committed.

## Signing

Local and CI builds never generate or commit a release key. Set all four
environment variables to produce a signed APK:

- `ANILIB_ANDROID_KEYSTORE`: path to the JKS or PKCS12 key store;
- `ANILIB_ANDROID_KEYSTORE_PASSWORD`: key-store password;
- `ANILIB_ANDROID_KEY_ALIAS`: signing-key alias;
- `ANILIB_ANDROID_KEY_PASSWORD`: signing-key password.

If any value is absent, Gradle deliberately emits a release APK whose filename
ends in `-unsigned.apk`. It is suitable for verification and later signing, but
cannot be installed or distributed as a trusted Anilib release until it is
signed.

The reusable `android-release.yml` workflow accepts a numeric semantic version
and a positive Android version code. Manual runs may deliberately produce an
unsigned verification artifact. A `vMAJOR.MINOR.PATCH` tag instead invokes it
through `application-release.yml`, uses the monotonic GitHub run number as the
version code, and requires all signing secrets. CI restores the base64-encoded
key from `ANILIB_ANDROID_KEYSTORE_BASE64`; the other secret names match the
environment variables above. Production rejects `-unsigned.apk`, verifies the
APK certificate with `apksigner`, and removes the temporary key store before
publication. The final release also carries a signed provenance attestation.

## User-supplied Aniyomi APKs

The shared extension-repository screen exposes `Install APK` only on Android.
Anilib revalidates HTTPS redirects, downloads through its shared cookie and
rate-limit stack with a 16 MiB bound, then writes the bytes into an Android
`PackageInstaller` session. Android's unknown-source permission and final user
confirmation remain mandatory.

This hand-off installs the user-selected package; it does not make the same APK
executable on desktop. On the next Android startup, certificate-trusted packages
pass a non-initializing host-ABI check. ABI-ready entrypoints are loaded before
the immutable product graph starts. Anime packages become Anilib catalogue and
streaming Sources; manga packages become catalogue and paged Reader Sources with
bounded image reads through the extension's own HTTP client. Failures remain
attached to one package. Current APKs stay blocked until Anilib supplies their
complete external host ABI.
