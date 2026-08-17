# Anilib Agent Guide

This file applies to the whole repository.

## Architecture

- Read `Anilib/ARCHITECTURE.md`, the owning `module.properties`, build file,
  and nearby verification code before editing a module.
- Keep dependencies inward-facing: Foundation, Framework, Kernel, Features,
  Configurations, then Platforms. Tooling may inspect all layers but production
  code never depends on Tooling.
- A feature is a vertical capability. Its API, behavior, platform-neutral UI
  model, resources, and Bundle stay under `Anilib/Features/<Feature>`.
- A Bundle is the only unit selected by a product configuration. Do not add a
  second registry or hidden classpath scanning mechanism.
- Platform SDK types belong only in the matching `Platforms` adapter.
- All Java packages start with `fr.vriege.anilib`.
- Do not add third-party runtime, build-plugin, or test-library dependencies.

## Quality

`Anilib/Tooling/JavaQuality` owns the dependency-free repository checker. Add
focused rules there and register them in `AnilibJavaRuleRegistry.standard()`.
Do not suppress a diagnostic or weaken a rule only to make the gate green.

Run the narrow owning task first, then the full gate:

```powershell
.\gradlew.bat --no-daemon --console=plain javaQuality
.\gradlew.bat --no-daemon --console=plain architectureTest
.\gradlew.bat --no-daemon --console=plain check
```

## Commits

Use small Conventional Commits. Stage explicit paths, inspect the staged diff,
and keep unrelated user work intact. Never commit generated build directories
or IDE metadata.
