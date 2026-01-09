# Project Loom Learning: Mastering Virtual Threads & Structured Concurrency

This project is a comprehensive learning resource for Java Project Loom, focusing on **Virtual Threads** and **Structured Concurrency**. It includes microservices, benchmarking scripts, performance analysis, and a series of detailed blog posts.

## Key Features

- **Virtual Threads (JEP 444):** High-throughput microservice implementations showing how to handle millions of concurrent connections.
- **Structured Concurrency (JEP 480):** Advanced patterns for managing task hierarchies, timeouts, and error propagation using `StructuredTaskScope`.
- **Performance Benchmarking:** Comparative analysis between Virtual Threads, Platform Threads, and Reactive programming.
- **Resilience Patterns:** Implementations of Circuit Breakers, Retries, and Fallbacks within a structured concurrency context.
- **JVM Monitoring:** Tools and scripts for monitoring memory usage, thread counts, and carrier thread pinning.

## Prerequisites

- **Java 21 (LTS)** or higher (with preview features enabled).
- **Maven** for building the project.
- **wrk** (optional, for benchmarking).
- **nc (netcat)** (optional, for script readiness checks).

## Project Structure

- `src/main/java/app/js/`:
  - `microservices/`: Diverse microservice implementations (Virtual, Platform, Optimized).
  - `concurrent/` & `structured/`: Structured concurrency examples and advanced patterns.
  - `pinning/`: Demonstrations of thread pinning and optimization techniques.
  - `monitoring/` & `observability/`: Tools for JVM and performance tracking.
- `scripts/`: A suite of shell scripts for automation, benchmarking, and monitoring.

## Microservices & Port Mapping

| Port | Service | Description |
|------|---------|-------------|
| 8080 | `VirtualThreadMicroservice` | Main service demonstrating virtual thread scalability. |
| 8081 | `PlatformThreadMicroservice` | Baseline service using traditional platform threads. |
| 8082 | `AdvancedStructuredConcurrencyMicroservice` | Advanced patterns like circuit breakers and retries. |
| 8083 | `JvmMonitoringService` | Provides Prometheus metrics and JVM information. |
| 8084 | `MemoryOptimizedMicroservice` | Benchmarks JVM memory optimization (ZGC vs G1GC). |
| 8085 | `StructuredMicroservice` | Clean implementations of structured concurrency patterns. |
| 8086 | `ThreadOptimizedMicroservice` | Benchmarks scheduler parallelism and pool sizes. |

## Build and Run

### Compile
```bash
mvn compile
```

### Run a Microservice
```bash
java --enable-preview -cp target/classes app.js.microservices.VirtualThreadMicroservice
```

### Run Benchmarks
You can run the full benchmarking suite which handles service startup and cleanup automatically:
```bash
./scripts/run_all_benchmarks.sh
```
See [scripts/README.md](scripts/README.md) for more details.

## Blog Series

### Mastering Java's Project Loom
1. **Virtual Threads Revolution** - Intro to Loom.
2. **High-Performance Web Services** - Solving the C10K problem.
3. **Real-World Microservices** - Production patterns and monitoring.
4. **Structured Concurrency** - Future of parallel programming.
5. **Advanced Patterns** - Aggregation, fallbacks, and circuit breakers.
6. **Performance Deep Dive** - Comparative analysis.
7. **Production Readiness** - Debugging and best practices.
8. **The Future of Java Concurrency** - Scoped values and beyond.

### Mastering Structured Concurrency
A focused series exploring `StructuredTaskScope`, covering timeouts, conditional cancellation, resource-aware scheduling, and hierarchical task management.

## 💡 Java 25 Structured Concurrency Example

In Java 25, `StructuredTaskScope` uses a `Joiner`-based approach:

```java
try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
    Subtask<String> task1 = scope.fork(() -> "Result A");
    Subtask<String> task2 = scope.fork(() -> "Result B");

    Stream<Subtask<String>> results = scope.join();
    results.forEach(s -> System.out.println(s.get()));
}
```

## Testing
To run a basic sanity check:
```bash
java --enable-preview -cp target/classes app.js.LoomSanityTest
```
