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

The `android-release.yml` workflow accepts a numeric semantic version and a
positive Android version code. Tag builds derive the semantic version from a
`vMAJOR.MINOR.PATCH` tag and use the monotonic GitHub run number as their version
code. CI optionally restores the base64-encoded key from
`ANILIB_ANDROID_KEYSTORE_BASE64`; the other secret names match the environment
variables above. The workflow always runs the complete repository gate and
uploads the APK together with `SHA256SUMS`.

## User-supplied Aniyomi APKs

The shared extension-repository screen exposes `Install APK` only on Android.
Anilib revalidates HTTPS redirects, downloads through its shared cookie and
rate-limit stack with a 16 MiB bound, then writes the bytes into an Android
`PackageInstaller` session. Android's unknown-source permission and final user
confirmation remain mandatory.

This hand-off installs the user-selected package; it does not claim binary API
compatibility or make the same APK executable on desktop. Discovery and the
best-effort Aniyomi API adapter remain a separate roadmap item.
