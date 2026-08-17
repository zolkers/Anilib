# AnilibJava

AnilibJava is the repository-specific Java quality engine. It uses only the JDK
and scans every owned module, including Tooling itself.

The standard registry currently enforces:

- the `fr.vriege.anilib` package root and source-directory correspondence;
- JDK/Anilib-only imports and no wildcard imports;
- complete module manifests, known direct dependencies, and inward layer flow;
- explicit manifest dependencies for cross-module Java imports;
- no third-party Gradle libraries or plugins;
- a compact whitespace and line-length baseline.

Run it directly or through the full gate:

```powershell
.\gradlew.bat --no-daemon --console=plain javaQuality
.\gradlew.bat --no-daemon --console=plain check
```

New rules implement `AnilibJavaRule`, return deterministic diagnostics, and are
registered in `AnilibJavaRuleRegistry.standard()`.
