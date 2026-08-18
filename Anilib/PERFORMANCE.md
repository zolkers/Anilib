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
