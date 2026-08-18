# Application Update

Application Update owns Anilib's stable and opt-in beta application release
channels. It is
separate from `Updates`, which checks library titles, and from Extension
Repository, which updates user-installed source Bundles.

The Bundle publishes a shared service and presentation. Stable selects the
latest non-prerelease GitHub release. Beta selects the newest stable or
`MAJOR.MINOR.PATCH-beta.NUMBER` release. The choice is persisted atomically.
The About screen presents the channel, generated changelog, source commit,
license, and release page.

Every release contains `anilib-update.manifest` and
`anilib-update.manifest.sig`. The runtime verifies the manifest with the pinned
Ed25519 public key, binds it to this repository, workflow, tag, release page,
source commit, platform, file name, byte length, SHA-256 digest, and HTTPS
download URL, then streams the installer into a managed directory. A second
verification of the completed regular file is mandatory before installation.
Symlinks, unexpected names, size changes, digest changes, signature failures,
cross-channel releases, redirects outside HTTPS, and oversized responses are
rejected.

Android owns PackageInstaller sessions and the unknown-source permission flow.
Desktop owns the native installer hand-off for MSI, DEB, and notarized DMG
artifacts. Neither adapter silently elevates privileges or bypasses the
operating system confirmation UI.

## Release key provisioning

Generate the key once with the dependency-free SourcePublisher key generator.
Store the PKCS#8 private key as the protected GitHub Actions secret
`ANILIB_UPDATE_PRIVATE_KEY_BASE64`; never commit it. Embed only the matching
X.509 public key in `ApplicationUpdatePlugin`. Key rotation requires shipping a
release that trusts the successor key before signing exclusively with it.

The application-release workflow fails closed when the secret is absent,
checks every producer checksum, signs the exact manifest bytes, publishes the
manifest beside all four installers, requests GitHub artifact provenance, and
marks beta tags as prereleases. The operational ceremony is recorded in
`distribution/RELEASE_ACCEPTANCE.md`.
