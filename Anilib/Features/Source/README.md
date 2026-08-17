# Source extension SDK

The Source feature owns the stable entry point for local and remote content
extensions. API version `1.0` deliberately covers registration and immutable
identity only. API version `1.1` adds the optional `CatalogueSource` contract
for browse, latest, search, filters, and preferences without breaking a source
that implements only `Source`. API version `1.2` adds capability-limited source
factories, exact network-origin grants, and observable installed-extension
metadata.

## Extension shape

A source implements `Source` and returns one immutable `SourceDescriptor`:

```java
SourceId sourceId = SourceId.of("example.catalog");
SourceExtensionManifest manifest = SourceExtensionManifest.networked(
        ComponentDescriptor.of("extension.example", "Example source", "1.0.0"),
        sourceId,
        Set.of(SourceNetworkOrigin.https("api.example.test")));

AnilibPlugin bundle = new SourceExtensionPlugin(manifest, context ->
        new ExampleCatalogueSource(sourceId, context.httpClient()));
```

The product configuration must select that Bundle explicitly. There is no JAR
scanning or global registry. `SourceExtensionPlugin` declares its dependency on
the Source registrar and, only when requested, the Network capability. Its
factory receives a `SourceExtensionContext` rather than the Kernel installation
context or global client. Registration is removed automatically during rollback
or product shutdown.

Network origins are exact scheme/host/port grants. HTTPS is the default;
declaring an HTTP origin also makes `CLEARTEXT_NETWORK` visible. Both platform
transports expose redirects rather than following them, so every redirect hop
must pass through the same origin check.

## Compatibility

- Source IDs use stable lowercase component-style identifiers.
- Language tags are normalized BCP 47 tags; `und` represents language-neutral
  content.
- A runtime accepts the same API major and an equal or older minor version.
- Duplicate source IDs and incompatible API versions fail product startup.
- Registry enumeration is ordered by source ID and returns immutable snapshots.

Extensions should depend only on `Features/Source/Api` plus the narrow optional
contracts they implement. They must not depend on Source Runtime or Bundle.
Repository modules declared with `layer=EXTENSION` are checked by AnilibJava:
direct JDK networking, filesystem access, reflection, Kernel access, and the raw
Network feature are rejected. This is an architectural capability boundary for
explicitly selected code, not a promise to execute arbitrary untrusted bytecode;
signing and update trust remain release-phase work.

## Catalogue shape

A catalogue source implements `CatalogueSource`. It receives immutable
`SourceBrowseRequest` and `SourceSearchRequest` values containing the selected
page, page size, validated filter values, and a preference snapshot. It returns
one immutable `SourcePage`; the shared Discovery feature handles cross-source
search, persistence, adding titles to Library, and migration.

Filter schemas cover headers, separators, text, checkboxes, tri-state values,
select lists, and sorts. Preference schemas cover switches, text, and select
lists. Platforms render those schemas; extensions never import Compose,
Android, or desktop UI types.
