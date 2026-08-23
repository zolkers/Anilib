# Anilib source template

This is a complete portable source repository example. Its Java packages use
`fr.vriege.anilib`, its module depends only on the Source SDK, and the produced
Bundle runs unchanged in the Desktop extension runtime.

## Build and publish locally

From the Anilib repository root:

```powershell
.\gradlew.bat :Anilib:Examples:SourceTemplate:jar
.\gradlew.bat :Anilib:Tooling:SourcePublisher:run --args="keygen private.key public.key"
.\gradlew.bat :Anilib:Tooling:SourcePublisher:run --args="publish private.key published Anilib/Examples/SourceTemplate/source-publisher.properties"
```

Keep `private.key` secret. Users import the content of `public.key` once, then
add either the published `index.json` URL or the GitHub repository URL in
Anilib. `published` contains the signed JAR plus full and minified indexes.
To keep an AniYomi/Mihon APK as a fallback during migration, add
`apk=path/to/fallback.apk` to `source-publisher.properties`. The publisher
keeps one logical `pkg` entry, copies the APK under `apk/`, prefers the signed
Bundle in both Anilib applications, and emits `checksums.sha256` for both files.

For a network source, request exact origins through
`source.N.origins` in `META-INF/anilib-extension.properties` and use only the
HTTP client supplied by `SourceExtensionContext`. The quality checker rejects
direct network, filesystem, reflection, Kernel, or platform access.

The repository workflow `source-template-release.yml` is ready to publish this
example to a `repo` branch. Copy it with the template when starting a standalone
source repository and configure `SOURCE_PUBLISHER_PRIVATE_KEY` with the Base64
PKCS#8 private key.
