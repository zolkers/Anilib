# Distribution metadata

This directory keeps store-neutral release copy and optional store submission
paths. GitHub Releases remains the mandatory signed distribution channel;
stores are adapters and never replace the reproducible Gradle artifacts or the
signed update manifest.

`store/windows` and `store/macos` contain the canonical
English listing copy. A submission may transform that copy into Partner Center
or App Store Connect formats. Store credentials, generated
screenshots, notarization tickets, certificates, and uploaded packages are not
committed.

Before any submission, complete `RELEASE_ACCEPTANCE.md`, copy the matching
signed artifact without rebuilding it, verify its published SHA-256 digest, and
record the external store release identifier in the acceptance record.
