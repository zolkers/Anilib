# Release acceptance

Copy this checklist into the tagged release notes and fill every evidence field.
An unchecked item blocks promotion from beta to stable.

## Provisioning

- [ ] The update private-key secret matches the public key pinned in the application.
- [ ] Windows certificate and password secrets are configured.
- [ ] macOS certificate, password, identity, Apple ID, team ID, and notarization-password secrets are configured.
- [ ] Protected environments limit secret access and tag creation to release maintainers.

The update secret name is `ANILIB_UPDATE_PRIVATE_KEY_BASE64`. The platform
secret names are documented in the Desktop release guide.

## Tagged workflow

- [ ] Immutable tag: `________________`
- [ ] Source commit: `________________________________________`
- [ ] Application workflow run: `________________`
- [ ] Windows producer run: `________________`
- [ ] Linux producer run: `________________`
- [ ] macOS producer run: `________________`
- [ ] All producer checksums pass before publication.
- [ ] GitHub provenance attestations exist for MSI, DEB, and DMG.
- [ ] The Ed25519 signature verifies the published update manifest.
- [ ] Manifest tag, commit, channel, artifact names, sizes, digests, and URLs match the release.

## Installation acceptance

- [ ] Windows: `%LOCALAPPDATA%\AnilibApp` is updated while `%LOCALAPPDATA%\Anilib` data is retained.
- [ ] Windows: Authenticode, install, update, launch, uninstall, and data retention verified.
- [ ] Linux: metadata, install, update, launch, uninstall, and data retention verified.
- [ ] macOS: signature, notarization, stapling, Gatekeeper, install, update, launch, and data retention verified.
- [ ] Stable ignores beta; beta sees the newest eligible release.
- [ ] Changelog, license, source commit, progress, failures, checksum rejection, and hand-off are verified.
- [ ] Offline restart leaves the existing installation and library usable.

## Optional stores

- [ ] Microsoft Partner Center release ID or `not submitted`: `________________`
- [ ] App Store Connect release ID or `not submitted`: `________________`
- [ ] Acceptance owner and UTC date: `________________`
