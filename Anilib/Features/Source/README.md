# Source extension SDK

The Source feature owns the stable entry point for local and remote content
extensions. API version `1.0` deliberately covers registration and immutable
identity only. API version `1.1` adds the optional `CatalogueSource` contract
for browse, latest, search, filters, and preferences without breaking a source
that implements only `Source`. Network access remains a separate capability.

## Extension shape

A source implements `Source` and returns one immutable `SourceDescriptor`:

```java
Source source = () -> new SourceDescriptor(
        SourceId.of("example.catalog"),
        "Example catalog",
        "1.0.0",
        "en",
        Set.of(SourceContentKind.MANGA),
        SourceSdk.API_VERSION);

AnilibPlugin bundle = new SourceExtensionPlugin(
        ComponentDescriptor.of("extension.example", "Example source", "1.0.0"),
        source);
```

The product configuration must select that Bundle explicitly. There is no JAR
scanning or global registry. `SourceExtensionPlugin` declares its dependency on
the Source registrar, registers during transactional installation, and removes
the source automatically during rollback or product shutdown.

## Compatibility

- Source IDs use stable lowercase component-style identifiers.
- Language tags are normalized BCP 47 tags; `und` represents language-neutral
  content.
- A runtime accepts the same API major and an equal or older minor version.
- Duplicate source IDs and incompatible API versions fail product startup.
- Registry enumeration is ordered by source ID and returns immutable snapshots.

Extensions should depend only on `Features/Source/Api` plus the narrow optional
contracts they implement. They must not depend on Source Runtime or Bundle.

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
