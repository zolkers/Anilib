# Source extension SDK

The Source feature owns the stable entry point for local and remote content
extensions. API version `1.0` deliberately covers registration and immutable
identity only. Browse, search, filters, preferences, and network access are
separate optional contracts so adding them does not break existing sources.

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
