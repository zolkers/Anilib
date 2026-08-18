# Security review

Review date: 2026-08-18. Scope: extension trust and loading, archive parsing,
WebView, loopback media relay, backup import, application updater, and release
supply chain. The review follows data from each untrusted entry point to its
bounded parser, trust decision, persistence boundary, and platform hand-off.

| Boundary | Enforced controls | Verification evidence | Residual decision |
| --- | --- | --- | --- |
| Extension repositories | User-entered HTTPS only, bounded redirects/indexes, no bundled catalogue | `ExtensionRepositoryTest`, `SourceExtensionIsolationRuleTest` | Repository metadata is untrusted until artifact verification |
| Portable Bundles | SHA-256, pinned Ed25519 publisher key, API preflight, bounded in-memory archive, isolated JPMS layer | `SourcePublisherTest`, `PortableBundleLoadingTest` | Publishers remain responsible for remote source behavior |
| Android APK bridge | HTTPS hand-off, package/certificate/ABI preflight, Android confirmation | Android release and adapter tests | Unsupported host bytecode is rejected |
| Local and extension archives | Entry, expanded-byte, descriptor, page-byte, path, duplicate, and symlink bounds | `LocalSourceTest`, `PortableBundleLoadingTest` | A 10,000-entry cap now also rejects local ZIP/CBZ/EPUB floods |
| WebView | Per-source headers and cookies, policy switches, platform chooser/pop-up/download hand-offs, explicit cleanup | browser policy and network tests | Web content remains sandboxed by the platform WebView engine |
| Loopback media relay | Loopback binding, random endpoint identity, request-header denylist, response-header allowlist, bounded lifecycle | `HttpFrameworkTest`, `PlayerTest` | Relay exposes no non-loopback listener |
| Backup import | Regular-file and symlink checks, total-size/section/count/string bounds, SHA-256 checksum, preview and rollback | `BackupTest` | Imported user data can replace state only after confirmation |
| Application updater | Pinned Ed25519 manifest, repository/workflow/tag/commit binding, HTTPS, exact name/size/SHA-256, regular-file recheck | `ApplicationUpdateTest`, `ApplicationReleaseRuleTest` | OS confirmation remains mandatory |
| Release supply chain | Required platform signing, checksums, immutable tag, signed manifest, provenance attestation, protected secret contract | release quality rules and `distribution/RELEASE_ACCEPTANCE.md` | External key custody and four-host install ceremony are operational controls |

No production module gains a Tooling dependency, no private key is committed,
and no parser suppresses a failed trust or bounds check. Findings that change a
security boundary require a focused regression test and an update to this table.
