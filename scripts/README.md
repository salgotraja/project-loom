# Project Loom Benchmarking & Testing Scripts

This directory contains a suite of scripts designed to test, monitor, and benchmark the Project Loom microservices.

## Port Mapping

The microservices are configured to run on the following ports to avoid conflicts:

| Port | Service | Description |
|------|---------|-------------|
| 8080 | `VirtualThreadMicroservice` | Main service demonstrating virtual thread scalability. |
| 8081 | `PlatformThreadMicroservice` | Baseline service using traditional platform threads. |
| 8082 | `AdvancedStructuredConcurrencyMicroservice` | Demonstrates advanced patterns like circuit breakers and retries. |
| 8083 | `JvmMonitoringService` | Provides Prometheus metrics and JVM information. |
| 8084 | `MemoryOptimizedMicroservice` | Benchmarks JVM memory optimization (ZGC vs G1GC). |
| 8085 | `StructuredMicroservice` | Clean implementations of structured concurrency patterns. |
| 8086 | `ThreadOptimizedMicroservice` | Benchmarks scheduler parallelism and pool sizes. |

## Script Overview

### Master Script
- **`run_all_benchmarks.sh`**: The recommended entry point. It starts all services, waits for them to be ready, runs all test and benchmark scripts, and automatically cleans up all processes on exit.

### Benchmarking Scripts
- **`benchmark.sh`**: Uses `wrk` to compare throughput and latency between virtual threads (8080) and platform threads (8081).
- **`benchmark-memory.sh`**: Benchmarks memory and thread optimizations on ports 8084 and 8086.

### Testing Scripts
- **`test_structured_concurrency.sh`**: Validates basic structured concurrency endpoints on port 8080.
- **`advanced_structured_concurrency_test.sh`**: Comprehensive test of resilience patterns on port 8082.
- **`test_clean_structured.sh`**: Tests the clean structured concurrency patterns on port 8085.
- **`structured_concurrency_test.sh`**: An alternative testing script for port 8080 with load testing capability.

### Monitoring Scripts
- **`monitor-jvm.sh`**: A background monitor that periodically polls memory, thread, and JVM metrics from the running services.

### Maintenance Scripts
- **`remove_emojis.py`**: A utility used to clean up decorative emojis from the blog articles.

## Prerequisites

- **Java 25**: Required for Project Loom features (with preview enabled).
- **Maven**: To build the project.
- **`wrk`**: Required for load benchmarking.
- **`nc` (netcat)**: Used by `run_all_benchmarks.sh` for readiness checks.

## Usage

To run the full suite:
```bash
./scripts/run_all_benchmarks.sh
```

To monitor JVM metrics while services are running:
```bash
./scripts/monitor-jvm.sh
```
