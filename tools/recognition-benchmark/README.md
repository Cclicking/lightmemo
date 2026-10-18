# Recognition speed comparison

Run from the repository root with the existing Android/JDK 21 build environment:

```powershell
python tools/benchmark_recognition.py --baseline d7c98c0f39920bfc91f76cf1048dacdd47503764
```

The runner extracts the selected Git version's two database-matching source files, renames their
types, and compiles them alongside the current implementation as temporary test sources. It
requires the same tracked database assets and uses Gradle offline mode. The temporary sources
are removed in `finally`; results are written to `build/recognition-benchmark/`.

The benchmark runs two warm-up rounds, alternates baseline/current execution order, checks
matching results and candidate ordering, and records raw timings, median, P95 and request counts.
It measures five or nine samples per scenario. Startup/compilation time is outside measurement.
Fresh-client database loading is included only in the explicitly marked cold-library case;
the JVM is warmed up and the operating system file cache is not cleared.

All HTTP is intercepted. Database responses have a fixed 120ms delay and an empty food list;
the text-model response has a fixed 150ms delay and an eight-component fixture. No credentials
or configured service endpoints are read. These scenarios measure request reuse and bounded
concurrency, not real internet or model performance.

The text scenario runs model-request construction, response parsing and database enrichment.
It excludes Android Service lifecycle, image decoding, rendering, personal-memory assistance
and persistence, so its result must not be presented as phone end-to-end latency.

The retained result from 2026-10-18 is in
[`docs/performance/recognition-2026-10-18/report.md`](../../docs/performance/recognition-2026-10-18/report.md).
