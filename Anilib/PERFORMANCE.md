# Performance budgets

Performance is a release gate, not a one-time observation. The dependency-free
architecture suite measures cold Standard-product startup and a deterministic
10,000-title durable library fixture on every supported development host.

| Workload | Blocking budget | Gate |
| --- | ---: | --- |
| Complete Standard startup | 20 seconds | `PerformanceBudgetTest` |
| Atomic write of 10,000 titles | 20 seconds | `PerformanceBudgetTest` |
| Reopen and index 10,000 titles | 10 seconds | `PerformanceBudgetTest` |
| Serialized 10,000-title catalog | 32 MiB | `PerformanceBudgetTest` |
| Compact/expanded route interaction | 100 ms p95 input latency | release acceptance |
| Reader decoded-page cache | 256 MiB maximum | platform profiler acceptance |
| Player relay startup | 1 second p95 to first relayed byte | platform profiler acceptance |
| Sustained download | at least 90% of direct transport throughput | platform profiler acceptance |
| Warm offline restart | 3 seconds to interactive shell | release acceptance |

The deterministic gate deliberately uses broad wall-clock ceilings so ordinary
shared CI variance does not cause flakes. Release acceptance records p50, p95,
peak resident memory, device/host, runtime version, library size, media fixture,
and profiler trace for scrolling, reader cache, player relay, downloads, and
offline restart. Any result over budget blocks promotion even when the unit gate
passes. Baselines use synthetic content and no third-party source repository.

## Reproducible headless audit

Run `:Anilib:Tooling:ArchitectureTests:performanceAudit` to execute the product
startup and 10,000-title workloads under Java Flight Recorder without opening a
platform window. The task always replaces
`build/reports/performance/anilib-headless-budget.jfr`, so a successful result
cannot accidentally reuse an older recording.

The 2026-08-19 Windows/JDK 21 audit measured 356 ms for Standard startup,
106 ms to atomically write 10,000 titles, 92 ms to reopen and index them, and
1.12 MiB on disk. The recording contained one 5.69 ms GC pause and no Java
monitor contention. These are a developer-host sample, not replacements for
the release p50/p95 acceptance runs.

That audit removed four structural hot paths: browser/JCEF initialization is
lazy, blocking source and media work leaves the Compose UI dispatcher, reader
page data is resolved once per chapter instead of once per recomposition, and
the desktop extension inventory is cached until install/uninstall mutation.
Library metadata validation also avoids streams, duplicate-check collections
for zero/one-value lists, and eagerly allocated success-path error messages.
