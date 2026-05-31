# Testing and Benchmarking Guide

This project is for learning and exploring Project Loom, virtual threads, and structured concurrency. Structured concurrency is still a preview API, so treat the benchmark results as local measurements of the checked-in examples, not production claims.

## Prerequisites

- Java 25 with preview features enabled
- Maven
- `curl`
- `wrk` for load benchmarks
- `nc` for the all-in-one benchmark runner readiness checks

Verify the local toolchain:

```bash
java -version
mvn -version
command -v wrk
command -v nc
```

## Build

Compile the project before running any article checks:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

The main branch now builds with OpenJDK 25.0.2 and uses the Java 25 preview structured-concurrency API, with the Java 21 version separately managed in the `feature/java-21` branch. Virtual threads are final in Java 21. Structured concurrency remains a preview API, and Article 9 covers what changed between the two versions.

## Start Services Manually

Start only the services needed for the article you are updating.

```bash
java --enable-preview -cp "$CP" app.js.microservices.VirtualThreadMicroservice
java --enable-preview -cp "$CP" app.js.structured.StructuredMicroservice
java --enable-preview -cp "$CP" app.js.microservices.AdvancedStructuredConcurrencyMicroservice
java --enable-preview -cp "$CP" app.js.monitoring.JvmMonitoringService
./scripts/run-memory-optimized.sh
./scripts/run-thread-optimized.sh
```

Port map:

| Port | Service |
|---:|---|
| 8080 | `VirtualThreadMicroservice` |
| 8081 | `PlatformThreadMicroservice` |
| 8082 | `AdvancedStructuredConcurrencyMicroservice` |
| 8083 | `JvmMonitoringService` |
| 8084 | `MemoryOptimizedMicroservice` |
| 8085 | `StructuredMicroservice` |
| 8086 | `ThreadOptimizedMicroservice` |

## Article Workflow

For each article, copy the existing article and edit the copy. Keep the original as the baseline.

```bash
cp blog/mastering-structured-concurrency/part-N-topic.md \
   blog/mastering-structured-concurrency/part-N-topic-modified.md

cp blog/mastering-java-project-loom/part-N-topic.md \
   blog/mastering-java-project-loom/part-N-topic-modified.md
```

Before editing the copy:

1. Build the project.
2. Identify the code files and scripts that back the article.
3. Start the relevant service or services.
4. Run endpoint checks.
5. Run load benchmarks sequentially.
6. Record the exact commands, local environment, and fresh results while working. Put only the results that teach the article's point into the article copy.

Do not use `/aggregate-old` for the structured-concurrency article measurements unless an article is explicitly comparing against `CompletableFuture`.

For the Java Project Loom series, add or refresh the article-specific section in this guide during the same pass as the article update. Do not invent instructions for articles that have not been grounded yet.

## Mastering Java Project Loom article checks

### Article 1: virtual thread basics

Article 1 uses the two small port 8080 servers:

- `src/main/java/app/js/PlatformThreadPoolServer.java`
- `src/main/java/app/js/VirtualThreadPoolServer.java`

Both examples expose `GET /api`, sleep for 200ms per request, and return a short text response. Run them one at a time because both bind to port 8080.

Build first:

```bash
mvn clean compile -DskipTests
```

Run the platform-thread version:

```bash
java --enable-preview -cp target/classes app.js.PlatformThreadPoolServer
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/api
wrk -t4 -c40 -d10s http://localhost:8080/api
```

Stop the server, then run the virtual-thread version:

```bash
java --enable-preview -cp target/classes app.js.VirtualThreadPoolServer
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/api
wrk -t4 -c40 -d10s http://localhost:8080/api
```

Capture:

- Java and Maven versions
- build result
- single-request response and status
- `wrk` average latency
- `wrk` requests/sec
- total requests
- exact load settings

Do not use `LoomThreadTest` or `VirtualThreadFlood` as the default Article 1 evidence. Those classes intentionally create very large thread counts and are stress experiments, not routine article checks.

### Article 2: virtual-thread web service routes

Article 2 uses `src/main/java/app/js/microservices/VirtualThreadMicroservice.java` on port 8080.

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Start the service:

```bash
java --enable-preview -cp "$CP" app.js.microservices.VirtualThreadMicroservice
```

Collect smoke-check evidence:

```bash
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/health
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/compute
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/block
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/file
curl -s http://localhost:8080/metrics
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/aggregate
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8080/compute
wrk -t4 -c40 -d10s http://localhost:8080/block
wrk -t4 -c40 -d10s http://localhost:8080/file
curl -s http://localhost:8080/metrics
```

Capture:

- Java and Maven versions
- build result
- endpoint response body and status
- `wrk` average latency
- `wrk` requests/sec
- total requests
- final `/metrics` output

Use the Article 2 results to explain route shape, not to make a universal performance claim. The `/block` route has a 300ms sleep, so with 40 clients the rough ceiling is about `40 / 0.3`, or 133 requests/sec. Check whether the measured value is in that neighborhood.

Do not use `scripts/benchmark.sh` for Article 2 unless both port 8080 and port 8081 are known to be running the expected services. The script compares `VirtualThreadMicroservice` on 8080 with `PlatformThreadMicroservice` on 8081; if another process owns 8081, do not force or manufacture the comparison.

### Article 3: microservice fan-out patterns

Article 3 uses `src/main/java/app/js/microservices/VirtualThreadMicroservice.java` and `scripts/test_structured_concurrency.sh` on port 8080.

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Start the service:

```bash
java --enable-preview -cp "$CP" app.js.microservices.VirtualThreadMicroservice
```

Run the endpoint script:

```bash
./scripts/test_structured_concurrency.sh
```

Collect direct endpoint evidence:

```bash
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/aggregate
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/first-success
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/multi-aggregate
curl -s http://localhost:8080/metrics
```

Capture fallback as a sequence because the endpoint includes a random failure path:

```bash
for i in $(seq 1 12); do
  printf 'fallback-%02d ' "$i"
  curl -s -w ' status=%{http_code} total=%{time_total}s\n' \
    http://localhost:8080/aggregate-with-fallback
done
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8080/aggregate
wrk -t4 -c40 -d10s http://localhost:8080/first-success
wrk -t4 -c40 -d10s http://localhost:8080/multi-aggregate
curl -s http://localhost:8080/metrics
```

For Article 3, do not benchmark `/aggregate-with-fallback` as if it were one stable path. Use the sequence to show both success and fallback behavior. The fallback can return much faster than the happy path because `fetchFileWithPossibleError()` may fail before the 300ms sibling branch completes.

### Article 4: structured concurrency ownership

Article 4 uses the standalone classes in `src/main/java/app/js/concurrent`.

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Run the small examples:

```bash
java --enable-preview -cp "$CP" app.js.concurrent.StructuredExample
java --enable-preview -cp "$CP" app.js.concurrent.StructuredExampleWithSuccess
java --enable-preview -cp "$CP" app.js.concurrent.StructuredExampleWithErrors
```

Run the comparison and tester:

```bash
java --enable-preview -cp "$CP" app.js.concurrent.StructuredConcurrencyComparison
java --enable-preview -cp "$CP" app.js.concurrent.StructuredConcurrencyTester
```

Capture:

- all-required scope output from `StructuredExample`
- first-success winner from `StructuredExampleWithSuccess`
- missing slower sibling completion in `StructuredExampleWithErrors`
- basic parallel timing from `StructuredConcurrencyComparison`
- first-success timing from `StructuredConcurrencyComparison`
- error timing from `StructuredConcurrencyTester`
- cancellation counters from `StructuredConcurrencyTester`

Do not turn the 1ms loop in `StructuredConcurrencyComparison` or `StructuredConcurrencyTester` into a broad performance claim. Use it as a local smoke check only. If the memory test reports negative deltas after GC, do not use those numbers as allocation evidence.

### Article 5: advanced structured-concurrency patterns

Article 5 uses the standalone advanced-pattern demo, the port 8082 advanced microservice, and the standalone circuit-breaker sequence:

- `src/main/java/app/js/structured/AdvancedStructuredPatterns.java`
- `src/main/java/app/js/microservices/AdvancedStructuredConcurrencyMicroservice.java`
- `src/main/java/app/js/microservices/ConcurrentServiceLayer.java`
- `src/main/java/app/js/structured/ScopedRequestHandler.java`
- `src/main/java/app/js/structured/ImprovedBusinessService.java`
- `src/main/java/app/js/structured/EnhancedCircuitBreakerDemo.java`
- `scripts/advanced_structured_concurrency_test.sh`

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Run the standalone advanced-pattern demo:

```bash
java --enable-preview -cp "$CP" app.js.structured.AdvancedStructuredPatterns
```

Capture the partial-results output, conditional-cancellation timing, progressive completion order, resource-aware result list, adaptive batch durations, and standalone bulkhead result. Do not claim early cancellation unless the elapsed time confirms that the parent returned before the slow siblings completed.

Start the port 8082 service:

```bash
java --enable-preview -cp "$CP" app.js.microservices.AdvancedStructuredConcurrencyMicroservice
```

Run the checked-in endpoint script:

```bash
./scripts/advanced_structured_concurrency_test.sh
```

Collect focused single-request evidence:

```bash
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8082/timeout/short
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8082/timeout/graceful
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8082/async/race
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8082/pattern/scatter-gather
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8082/pattern/bulkhead
```

Collect the retry endpoint as a sequence because its counter is stored on the service layer and spans requests:

```bash
for i in $(seq 1 6); do
  printf 'retry-%02d ' "$i"
  curl -s -w ' status=%{http_code} total=%{time_total}s\n' \
    http://localhost:8082/service/retry
done
```

The HTTP circuit-breaker endpoint uses random failures and resets on success. Run it as a sequence if you want endpoint evidence, but do not claim the open-breaker path unless the sequence actually reaches it:

```bash
for i in $(seq 1 25); do
  printf 'breaker-%02d ' "$i"
  curl -s -w ' status=%{http_code} total=%{time_total}s\n' \
    http://localhost:8082/service/circuit-breaker
done
```

Use the standalone circuit-breaker demo for deterministic state-transition evidence:

```bash
java --enable-preview -cp "$CP" app.js.structured.EnhancedCircuitBreakerDemo
```

Run focused load benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8082/timeout/graceful
wrk -t4 -c40 -d10s http://localhost:8082/async/race
wrk -t4 -c40 -d10s http://localhost:8082/pattern/scatter-gather
wrk -t4 -c40 -d10s http://localhost:8082/pattern/bulkhead
wrk -t2 -c20 -d10s http://localhost:8082/timeout/short
curl -s http://localhost:8082/metrics
```

For Article 5, the `/timeout/short` endpoint checks its deadline after `scope.join()`, so a 300ms deadline can still produce a response around 500ms. Treat that as a policy finding, not as a benchmark anomaly. The `/service/retry` endpoint shows stateful behavior across requests; use `EnhancedCircuitBreakerDemo` for the per-call retry helper in `ScopedRequestHandler.runWithRetry(...)`. The `/pattern/bulkhead` endpoint separates critical and non-critical scopes but still waits for the non-critical scope before returning. To test the adaptive-concurrency increase and decrease branches, change the standalone demo's simulated task delays below 100ms or above 500ms, then rerun `AdvancedStructuredPatterns`.

### Article 6: performance deep dive

Article 6 uses these checked-in files:

- `src/main/java/app/js/PlatformThreadPoolServer.java`
- `src/main/java/app/js/VirtualThreadPoolServer.java`
- `src/main/java/app/js/microservices/VirtualThreadMicroservice.java`
- `src/main/java/app/js/threads/ThreadOptimizedMicroservice.java`
- `src/main/java/app/js/reactive/VirtualThreadReactiveIntegration.java`
- `scripts/run-thread-optimized.sh`

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Run the standalone reactive integration demo. Use it as a wait-parallelization demo, not as a reactive-framework benchmark:

```bash
java --enable-preview -cp "$CP" app.js.reactive.VirtualThreadReactiveIntegration
```

Run the platform-thread pool server and virtual-thread pool server one at a time because both bind to port 8080:

```bash
java --enable-preview -cp "$CP" app.js.PlatformThreadPoolServer
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/api
wrk -t4 -c200 -d10s http://localhost:8080/api
```

Stop it, then run:

```bash
java --enable-preview -cp "$CP" app.js.VirtualThreadPoolServer
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/api
wrk -t4 -c200 -d10s http://localhost:8080/api
```

Start `VirtualThreadMicroservice` on port 8080:

```bash
java --enable-preview -cp "$CP" app.js.microservices.VirtualThreadMicroservice
```

Collect single-request evidence:

```bash
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/compute
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/block
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8080/file
```

Run focused 8080 load benchmarks sequentially:

```bash
wrk -t4 -c200 -d10s http://localhost:8080/compute
wrk -t4 -c200 -d10s http://localhost:8080/block
wrk -t4 -c200 -d10s http://localhost:8080/file
curl -s http://localhost:8080/metrics
```

Start the thread-optimized service on port 8086. Build the classpath first and pass `CP`, or rely on the script's `cp.txt` path:

```bash
./scripts/run-thread-optimized.sh
```

Collect single-request evidence:

```bash
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8086/compute-optimized
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8086/io-optimized
curl -s -w '\nstatus=%{http_code} total=%{time_total}s\n' http://localhost:8086/mixed-workload
curl -s http://localhost:8086/thread-stats
```

Run focused 8086 load benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8086/compute-optimized
wrk -t4 -c200 -d10s http://localhost:8086/io-optimized
wrk -t4 -c80 -d10s http://localhost:8086/mixed-workload
curl -s http://localhost:8086/thread-stats
```

For Article 6, do not publish large concurrency, memory, cloud-cost, or reactive-framework comparison tables unless you have actually run those experiments. The checked-in `scripts/benchmark.sh` assumes port 8081 is the platform-thread microservice; if another process owns 8081, do not use that script for comparison data. Prefer focused route-level `wrk` commands and explain the expected ceiling from the fixed delay and connection count.

### Article 7: monitoring and debugging virtual threads

Article 7 uses these checked-in files:

- `src/main/java/app/js/monitoring/JvmMonitoringService.java`
- `src/main/java/app/js/observability/VirtualThreadObservability.java`
- `src/main/java/app/js/pinning/VirtualThreadPinningDetector.java`
- `src/main/java/app/js/pinning/PinningOptimizationExample.java`
- `scripts/monitor-jvm.sh`

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Run the task-level observability demo:

```bash
java --enable-preview -cp "$CP" app.js.observability.VirtualThreadObservability
```

Capture the periodic monitor lines, total task count, success count, failure count, average execution time, error breakdown, memory summary, and `ThreadMXBean` thread counts. The error count varies because the error-tracking section uses `ThreadLocalRandom.current().nextBoolean()`. The per-task breakdown currently prints many entries without line breaks, so prefer the summary counters unless that formatting has been fixed.

Start the JVM monitoring service with an actual JFR recording from the JVM command line:

```bash
java --enable-preview \
  -XX:+FlightRecorder \
  -XX:StartFlightRecording=duration=20s,filename=part7-monitoring.jfr \
  -cp "$CP" \
  app.js.monitoring.JvmMonitoringService
```

Collect endpoint evidence:

```bash
curl -s http://localhost:8083/metrics
curl -s http://localhost:8083/jvm-info
jfr summary part7-monitoring.jfr
```

`JvmMonitoringService.startJFRProfiling()` only logs suggested JVM arguments. It does not start a recording by itself. Treat `/metrics` as heap and GC evidence, not as virtual-thread task evidence. Check the JFR summary before claiming virtual-thread events; in the current short service run, `jdk.VirtualThreadStart` and `jdk.VirtualThreadPinned` were both zero.

Run the pinning detector:

```bash
java --enable-preview \
  -Djdk.tracePinnedThreads=full \
  -cp "$CP" \
  app.js.pinning.VirtualThreadPinningDetector
```

Capture the heavy synchronized duration, the optimized `ReentrantLock` duration, the metrics averages, and whether any pinned-thread stack traces actually appear. Do not claim pinning from the log labels alone. On Java 25, the current run did not emit pinned-thread stack traces and the synchronized and `ReentrantLock` averages were effectively equal.

Run the lock-choice examples:

```bash
java --enable-preview \
  -Djdk.tracePinnedThreads=full \
  -cp "$CP" \
  app.js.pinning.PinningOptimizationExample
```

Use the completed sections only. The current first three sections compare synchronized versus `ReentrantLock`, synchronized cache access versus `ConcurrentHashMap`, and `ReentrantLock` versus `StampedLock`. The producer-consumer section starts 50 producers and 150 consumers, and each consumer calls `queue.take()`, so the program can wait forever. Do not publish a completion time for that section unless the code or test shape has been corrected and rerun.

The `scripts/monitor-jvm.sh` helper polls ports 8084, 8086, and 8083 in continuous loops. Use it only when all three services are already running and you want live polling. For article evidence, direct `curl` commands are easier to bound and reproduce.

### Article 8: future-facing concurrency planning

Article 8 uses the local examples that show API boundaries rather than projected roadmap claims:

- `src/main/java/app/js/scoped/ScopedValueExample.java`
- `src/main/java/app/js/reactive/VirtualThreadReactiveIntegration.java`
- `src/main/java/app/js/structured/AdvancedStructuredPatterns.java`

Build and create the runtime classpath:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"
```

Run the scoped-values demo:

```bash
java --enable-preview -cp "$CP" app.js.scoped.ScopedValueExample
```

Capture the request context lines, the structured-concurrency task results, the plain child virtual-thread inheritance check, and the `ThreadLocal` versus `ScopedValue` timing. Do not turn the micro-timing into a broad performance claim. In the current run, scoped values flowed through the structured scope, but the plain `Thread.startVirtualThread(...)` child and grandchild printed `unknown` for the scoped values.

Run the reactive bridge demo:

```bash
java --enable-preview -cp "$CP" app.js.reactive.VirtualThreadReactiveIntegration
```

Capture the publisher ordering, the five-item bridge result, the backpressure buffer sequence, the error-handling summary, and the 1,000-item wait-parallelization timings. Treat the performance section as serial waiting versus concurrent waiting, not as a benchmark against reactive frameworks.

Run the advanced-pattern demo:

```bash
java --enable-preview -cp "$CP" app.js.structured.AdvancedStructuredPatterns
```

Capture the partial-results output, conditional-cancellation timing, progressive summary, adaptive batch lines, and bulkhead output. For Article 8, use these as planning evidence for API boundaries and policy tests. Do not repeat the old roadmap projection tables unless they have current primary-source backing and checked-in measurements.

When writing Article 8, verify current JEP status from OpenJDK primary sources. The current article uses:

- JEP 444 for virtual threads in Java 21
- JEP 491 for synchronized virtual-thread pinning changes in Java 24
- JEP 505 for structured concurrency in Java 25
- JEP 506 for scoped values in Java 25
- JEP 525 for structured concurrency in Java 26

## Endpoint Checks

Port 8080 structured examples:

```bash
./scripts/test_structured_concurrency.sh
```

Port 8085 clean structured examples:

```bash
./scripts/test_clean_structured.sh
```

Port 8082 advanced structured examples:

```bash
./scripts/advanced_structured_concurrency_test.sh
```

## Focused Structured Benchmarks

Run these one at a time. Do not overlap benchmarks against the same service when collecting article numbers.

```bash
wrk -t4 -c100 -d10s http://localhost:8080/aggregate
wrk -t4 -c100 -d10s http://localhost:8080/first-success
wrk -t4 -c100 -d10s http://localhost:8080/multi-aggregate
wrk -t4 -c100 -d10s http://localhost:8085/services/aggregate
```

Capture:

- command
- average latency
- requests/sec
- total requests
- date
- Java and Maven versions
- any service metrics endpoint output

## Mastering Structured Concurrency article checks

### Article 1: basic fan-out and scope lifetime

Start `VirtualThreadMicroservice` on port 8080 and `StructuredMicroservice` on port 8085.

Run the endpoint scripts:

```bash
./scripts/test_structured_concurrency.sh
./scripts/test_clean_structured.sh
```

Collect direct endpoint evidence:

```bash
curl -i -s http://localhost:8080/aggregate
curl -i -s http://localhost:8080/first-success
curl -i -s http://localhost:8080/multi-aggregate
curl -i -s http://localhost:8085/services/aggregate
curl -i -s 'http://localhost:8085/user/profile?userId=john123'
curl -i -s 'http://localhost:8085/user/dashboard?userId=john123'
curl -i -s 'http://localhost:8085/order/process?orderId=ORD-123'
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c100 -d10s http://localhost:8080/aggregate
wrk -t4 -c100 -d10s http://localhost:8080/first-success
wrk -t4 -c100 -d10s http://localhost:8080/multi-aggregate
wrk -t4 -c100 -d10s http://localhost:8085/services/aggregate
```

Use Article 1 to show that request duration tracks the slowest dependency inside the scope, not to claim structured concurrency is universally faster.

### Article 2: timeouts and partial results

Start `AdvancedStructuredConcurrencyMicroservice` on port 8082 and `StructuredMicroservice` on port 8085.

```bash
curl -i -s http://localhost:8082/timeout/short
curl -i -s http://localhost:8082/timeout/graceful
curl -i -s http://localhost:8082/deadline/strict
curl -i -s 'http://localhost:8085/timed/operation?op=slow-task'
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8082/timeout/short
wrk -t4 -c40 -d10s http://localhost:8082/timeout/graceful
wrk -t4 -c40 -d10s http://localhost:8082/deadline/strict
wrk -t2 -c20 -d10s 'http://localhost:8085/timed/operation?op=slow-task'
```

For partial-results behavior, run `AdvancedStructuredPatterns` and use only the timeout-with-partial-results output:

```bash
java --enable-preview -cp "$CP" app.js.structured.AdvancedStructuredPatterns
```

### Article 3: conditional cancellation and circuit breakers

Run the standalone circuit-breaker demo:

```bash
java --enable-preview -cp "$CP" app.js.structured.EnhancedCircuitBreakerDemo
```

Start `AdvancedStructuredConcurrencyMicroservice` on port 8082 and `StructuredMicroservice` on port 8085, then collect endpoint evidence:

```bash
curl -i -s 'http://localhost:8085/data/with-fallback?key=userdata'
curl -i -s 'http://localhost:8085/retry/operation?op=important-task'
curl -i -s http://localhost:8082/service/retry
curl -i -s http://localhost:8082/pattern/bulkhead
```

For breaker state, run repeated calls and capture the sequence:

```bash
for i in $(seq 1 12); do
  printf 'protected-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8085/protected/service?req=test-request'
done

for i in $(seq 1 12); do
  printf 'advanced-cb-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8082/service/circuit-breaker'
done
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8082/pattern/bulkhead
wrk -t4 -c40 -d10s 'http://localhost:8085/data/with-fallback?key=userdata'
```

### Article 4: progressive results and hierarchical task management

Article 4 uses standalone classes rather than HTTP endpoints.

Run the progressive and hierarchical demo:

```bash
java --enable-preview -cp "$CP" app.js.structured.ProgressiveHierarchicalDemo
```

Capture the progressive five-task summary, the hierarchical sequence duration, the combined level timings, and the e-commerce phase timings. The demo uses simulated sleeps and some simulated temporary failures, so distinguish terminal completion from successful business output when writing article text.

Run the dedicated benchmark:

```bash
java --enable-preview -cp "$CP" app.js.structured.ProgressiveResultsBenchmark
```

The benchmark currently runs four 30-second scenarios with 100 concurrent users:

- traditional blocking
- progressive results
- hierarchical task management
- combined progressive and hierarchical

Capture the raw result table, progress update counts, final task counters, and memory summary. Prefer the raw table over the generated improvement summary when writing the article, because the raw table is easier to interpret without overclaiming.

If the demo or benchmark reports `join not called` or `Owner did not join after forking`, the Java 25 migrated code is reading subtask results before the scope owner has joined. Fix the owner-join ordering before collecting article measurements.

### Article 5: resource-aware scheduling and bulkheads

Run the standalone advanced patterns class and capture the resource-aware, adaptive-concurrency, and bulkhead sections:

```bash
java --enable-preview -cp "$CP" app.js.structured.AdvancedStructuredPatterns
```

For Article 5, the useful standalone output is:

- `Resource-aware results`
- each `Batch completed` line from adaptive concurrency
- `Adaptive results`
- `Critical bulkhead completed`
- `Normal bulkhead completed`
- `Bulkhead results`

If this class fails before Pattern 5 with `join not called` or `Owner did not join after forking`, fix the owner-join ordering before collecting article measurements.

Start `AdvancedStructuredConcurrencyMicroservice` on port 8082, then collect single-request evidence:

```bash
curl -i -s http://localhost:8082/pattern/scatter-gather
curl -i -s http://localhost:8082/pattern/bulkhead
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8082/pattern/scatter-gather
wrk -t4 -c40 -d10s http://localhost:8082/pattern/bulkhead
curl -s http://localhost:8082/metrics
```

When writing the article, distinguish grouping from capacity limiting. The checked-in resource-aware scheduler groups tasks by type; it does not enforce a semaphore or pool permit limit.

### Article 6: composing resilience policies

Start `StructuredMicroservice` on port 8085.

Collect single-request evidence:

```bash
curl -i -s 'http://localhost:8085/services/aggregate'
curl -i -s 'http://localhost:8085/data/with-fallback?key=userdata'
curl -i -s 'http://localhost:8085/retry/operation?op=important-task'
curl -i -s 'http://localhost:8085/timed/operation?op=slow-task'
curl -i -s 'http://localhost:8085/order/process?orderId=ORD-123'
curl -i -s 'http://localhost:8085/cache/data?key=userdata'
curl -i -s 'http://localhost:8085/user/dashboard?userId=john123'
curl -i -s 'http://localhost:8085/user/profile?userId=john123'
```

Capture retry as a sequence because one request cannot show first-attempt success, later-attempt success, and exhausted retries:

```bash
for i in $(seq 1 10); do
  printf 'retry-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8085/retry/operation?op=important-task'
done
```

Capture circuit-breaker state as a sequence:

```bash
for i in $(seq 1 15); do
  printf 'protected-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8085/protected/service?req=test-request'
done
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s 'http://localhost:8085/data/with-fallback?key=userdata'
wrk -t4 -c40 -d10s 'http://localhost:8085/services/aggregate'
wrk -t4 -c40 -d10s 'http://localhost:8085/order/process?orderId=ORD-123'
wrk -t2 -c20 -d10s 'http://localhost:8085/timed/operation?op=slow-task'
```

When writing Article 6, identify the policy path rather than only the HTTP status: primary success, fallback used, retry exhausted, breaker open, timeout wrapper succeeded, or staged workflow completed. The checked-in timeout helper checks the deadline after `join()`, so it does not enforce the deadline at the wait boundary.

### Article 7: advanced fan-out patterns

Run the standalone first-success demo:

```bash
java --enable-preview -cp "$CP" app.js.concurrent.StructuredExampleWithSuccess
```

Run the standalone advanced-patterns class and capture only the sections relevant to Article 7:

```bash
java --enable-preview -cp "$CP" app.js.structured.AdvancedStructuredPatterns
```

For Article 7, the useful standalone output is:

- `Service-C` as the first successful result from `StructuredExampleWithSuccess`
- `Completed: 2/4 tasks`, `Results: [Quick result, Medium result]`, and `Timed out: [2, 3]`
- progressive callback order and the final progressive summary

Start `VirtualThreadMicroservice` on port 8080, `AdvancedStructuredConcurrencyMicroservice` on port 8082, and `StructuredMicroservice` on port 8085. Collect single-request evidence:

```bash
curl -i -s http://localhost:8080/first-success
curl -i -s http://localhost:8082/timeout/graceful
curl -i -s http://localhost:8082/async/race
curl -i -s 'http://localhost:8085/cache/data?key=userdata'
curl -i -s 'http://localhost:8085/data/with-fallback?key=userdata'
```

Capture sequences for the policy paths that vary:

```bash
for i in $(seq 1 12); do
  printf 'cache-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8085/cache/data?key=userdata'
done

for i in $(seq 1 12); do
  printf 'fallback-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8085/data/with-fallback?key=userdata'
done

for i in $(seq 1 5); do
  printf 'hedge-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' \
    'http://localhost:8082/async/race'
done
```

Run focused benchmarks sequentially:

```bash
wrk -t4 -c40 -d10s http://localhost:8082/timeout/graceful
wrk -t4 -c40 -d10s http://localhost:8080/first-success
wrk -t4 -c40 -d10s 'http://localhost:8085/cache/data?key=userdata'
wrk -t4 -c40 -d10s http://localhost:8082/async/race
wrk -t4 -c40 -d10s 'http://localhost:8085/data/with-fallback?key=userdata'
```

When writing Article 7, keep the pattern names tied to their contracts. First success returns one successful answer from equivalent sources. Partial results return completed sections and name the missing ones. A hedged read uses delayed duplicate work, so mention whether the hedge fired. Fallback is not a race: it should show primary success and fallback-after-primary-failure as separate paths.

### Article 8: operational checks

Start the services that expose the operational evidence:

```bash
java --enable-preview -cp "$CP" app.js.microservices.AdvancedStructuredConcurrencyMicroservice
java --enable-preview -cp "$CP" app.js.monitoring.JvmMonitoringService
java --enable-preview -cp "$CP" app.js.memory.MemoryOptimizedMicroservice
java --enable-preview -cp "$CP" app.js.threads.ThreadOptimizedMicroservice
```

Collect single-request evidence:

```bash
curl -i -s http://localhost:8082/timeout/short
curl -i -s http://localhost:8082/deadline/strict
curl -i -s http://localhost:8082/pattern/bulkhead
curl -s http://localhost:8082/metrics
curl -s http://localhost:8083/metrics
curl -s http://localhost:8083/jvm-info
curl -i -s http://localhost:8084/file-io
curl -s http://localhost:8084/memory-stats
curl -i -s http://localhost:8086/io-optimized
curl -i -s http://localhost:8086/mixed-workload
curl -s http://localhost:8086/thread-stats
```

Capture timeout behavior as a sequence:

```bash
for i in $(seq 1 3); do
  printf 'short-timeout-%02d ' "$i"
  curl -s -w ' status=%{http_code}\n' http://localhost:8082/timeout/short
done
```

Run focused load checks sequentially:

```bash
wrk -t2 -c20 -d10s http://localhost:8082/timeout/short
curl -s http://localhost:8082/metrics

wrk -t2 -c20 -d10s http://localhost:8082/deadline/strict

wrk -t2 -c20 -d10s http://localhost:8086/io-optimized
curl -s http://localhost:8086/thread-stats

wrk -t2 -c20 -d10s http://localhost:8086/compute-optimized
curl -s http://localhost:8086/thread-stats

wrk -t2 -c20 -d10s http://localhost:8084/file-io
curl -s http://localhost:8084/memory-stats
curl -s http://localhost:8084/gc
curl -s http://localhost:8084/memory-stats
```

For thread counters during load, start the `wrk` command first and query `/thread-stats` while it is still running. For memory counters, do not treat endpoint memory usage as retained heap. In the checked-in service it is cumulative per-request memory delta. Avoid `/memory-leak` unless the goal is explicitly to stress the heap.

When writing Article 8, make the observability gaps explicit. The current timeout endpoint checks the deadline after `join()`, so a 300ms budget can fail after roughly 500ms. The current advanced metrics endpoint increments `totalRequests` on success but not on timeout, while timeout durations still contribute to total response time.

### Article 9: Java 21 to Java 25 migration

Record the toolchain first:

```bash
java -version
mvn -version
command -v wrk
```

Compare the Java 21 branch with the current Java 25 code without switching branches:

```bash
git branch --list --all
git diff --stat feature/java-21..HEAD -- pom.xml src/main/java
git diff --name-only feature/java-21..HEAD -- pom.xml src/main/java
git diff --unified=6 feature/java-21..HEAD -- src/main/java/app/js/structured/ScopedRequestHandler.java
git diff --unified=6 feature/java-21..HEAD -- src/main/java/app/js/microservices/ConcurrentServiceLayer.java
git diff --unified=6 feature/java-21..HEAD -- src/main/java/app/js/structured/AdvancedStructuredPatterns.java
git diff --unified=6 feature/java-21..HEAD -- src/main/java/app/js/structured/HierarchicalProgressiveHandler.java
git diff --unified=6 feature/java-21..HEAD -- src/main/java/app/js/scoped/ScopedValueExample.java pom.xml
```

Count the migration patterns:

```bash
git grep -h -o 'StructuredTaskScope\.ShutdownOnFailure' feature/java-21 -- src/main/java | wc -l
git grep -h -o 'StructuredTaskScope\.ShutdownOnSuccess' feature/java-21 -- src/main/java | wc -l
git grep -h -o 'throwIfFailed' feature/java-21 -- src/main/java | wc -l
git grep -n 'scope\.joinUntil\|joinUntil(' feature/java-21 -- src/main/java
git grep -h -o 'StructuredTaskScope.open' HEAD -- src/main/java | wc -l
git grep -h -o 'StructuredTaskScope.Joiner' HEAD -- src/main/java | wc -l
rg -n 'scope\.joinUntil|joinUntil\(' src/main/java
```

Verify scoped values separately. In this repository, the Java 21 branch already uses fluent `ScopedValue.where(...)`, so do not claim a `runWhere` to `where` migration unless you have a separate source for it:

```bash
git grep -n 'ScopedValue\.runWhere\|ScopedValue\.callWhere\|ScopedValue\.where' feature/java-21 -- src/main/java
rg -n 'ScopedValue\.runWhere|ScopedValue\.callWhere|ScopedValue\.where' src/main/java
```

Run the current Java 25 examples:

```bash
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
export CP="$(cat cp.txt):target/classes"

java --enable-preview -cp "$CP" app.js.concurrent.StructuredExampleWithSuccess
java --enable-preview -cp "$CP" app.js.structured.AdvancedStructuredPatterns
java --enable-preview -cp "$CP" app.js.structured.ProgressiveHierarchicalDemo
java --enable-preview -cp "$CP" app.js.scoped.ScopedValueExample
```

Capture the standalone outputs that prove behavior after the migration: all-success completion, first-success selection, timeout/cancellation summaries, adaptive batch size, bulkhead counts, progressive execution totals, and scoped-value visibility in structured subtasks.

Start the Java 25 HTTP services and run focused smoke checks:

```bash
java --enable-preview -cp "$CP" app.js.microservices.AdvancedStructuredConcurrencyMicroservice
java --enable-preview -cp "$CP" app.js.structured.StructuredMicroservice

curl -i -s http://localhost:8082/structured/aggregate
curl -i -s http://localhost:8082/timeout/graceful
curl -i -s http://localhost:8082/async/race
curl -i -s http://localhost:8082/timeout/short
curl -i -s http://localhost:8085/services/aggregate
curl -i -s 'http://localhost:8085/timed/operation?op=slow-task'
curl -i -s 'http://localhost:8085/cache/data?key=userdata'
```

Run a small aggregate benchmark on each service:

```bash
wrk -t2 -c20 -d10s http://localhost:8082/structured/aggregate
wrk -t2 -c20 -d10s http://localhost:8085/services/aggregate
```

When writing Article 9, separate migration evidence from performance claims. The current pass verifies that Java 25 code compiles and preserves the checked-in example behavior. It does not compare Java 21 runtime performance unless you also build and run `feature/java-21` under JDK 21 with the same load settings.

## Full Benchmark Suite

Use the full runner when you want a broad local smoke test across services:

```bash
./scripts/run_all_benchmarks.sh
```

This starts the services, waits for ports 8080-8086, runs the endpoint checks, runs `wrk` benchmarks when available, and cleans up the Java processes it started.

## Virtual vs Platform Thread Benchmarks

With ports 8080 and 8081 running:

```bash
./scripts/benchmark.sh
```

This compares `/compute`, `/block`, and `/file` on `VirtualThreadMicroservice` and `PlatformThreadMicroservice`.

## Memory and Thread Tuning Benchmarks

To start and benchmark the memory and thread optimized services:

```bash
./scripts/benchmark-memory.sh
```

To monitor already-running services:

```bash
./scripts/monitor-jvm.sh
```

## Cleanup

Stop manually started services with `Ctrl-C`. Remove generated classpath and temporary benchmark files when they are no longer needed:

```bash
rm -f cp.txt microservice_test_file.txt
```

JFR files such as `memory-optimized.jfr` and `thread-optimized.jfr` are generated by the optimized service scripts. Keep them only when you are analyzing a run.
