# Contributing

Every change must preserve the module dependency direction documented in
`Anilib/ARCHITECTURE.md`, keep neutral modules free of platform types, and pass
the complete `check` task.

Use small Conventional Commits with lowercase imperative subjects:

- `feat:` user-visible capability or public API;
- `fix:` defect correction;
- `refactor:` behavior-preserving design change;
- `test:` test or architecture coverage only;
- `build:` build, module, or toolchain wiring;
- `docs:` documentation only;
- `chore:` repository maintenance.

Prefer one coherent concern per commit. Module declarations, contracts,
implementations, tests, and documentation should remain independently
reviewable whenever practical.
