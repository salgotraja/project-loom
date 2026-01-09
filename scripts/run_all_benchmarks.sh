#!/bin/bash

echo " Starting Project Loom Benchmarking Suite"
echo "=========================================="

cleanup() {
    echo " Cleaning up processes..."
    if [ ! -z "$PIDS" ]; then
        kill $PIDS 2>/dev/null
    fi
    echo " Cleanup complete."
}

trap cleanup EXIT

if ! command -v wrk &> /dev/null; then
    echo " wrk is not installed. Benchmarking results will be limited."
fi

echo " Building project..."
mvn clean compile -DskipTests
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP=$(cat cp.txt):target/classes
export CP

echo " Starting Microservices..."
PIDS=""

java --enable-preview -cp "$CP" app.js.microservices.VirtualThreadMicroservice > /dev/null 2>&1 &
PIDS="$PIDS $!"
java --enable-preview -cp "$CP" app.js.microservices.PlatformThreadMicroservice > /dev/null 2>&1 &
PIDS="$PIDS $!"
java --enable-preview -cp "$CP" app.js.microservices.AdvancedStructuredConcurrencyMicroservice > /dev/null 2>&1 &
PIDS="$PIDS $!"
java --enable-preview -cp "$CP" app.js.monitoring.JvmMonitoringService > /dev/null 2>&1 &
PIDS="$PIDS $!"
./scripts/run-memory-optimized.sh > /dev/null 2>&1 &
PIDS="$PIDS $!"
./scripts/run-thread-optimized.sh > /dev/null 2>&1 &
PIDS="$PIDS $!"
java --enable-preview -cp "$CP" app.js.structured.StructuredMicroservice > /dev/null 2>&1 &
PIDS="$PIDS $!"

echo " Waiting for services to initialize..."
wait_for_port() {
    local port=$1
    local name=$2
    local retries=30
    while ! nc -z localhost $port >/dev/null 2>&1 && [ $retries -gt 0 ]; do
        sleep 1
        let retries--
    done
    if [ $retries -eq 0 ]; then
        echo " Timeout waiting for $name on port $port"
        return 1
    fi
    echo " $name is ready on port $port"
    return 0
}

wait_for_port 8080 "VirtualThreadMicroservice"
wait_for_port 8081 "PlatformThreadMicroservice"
wait_for_port 8082 "AdvancedStructuredConcurrencyMicroservice"
wait_for_port 8083 "JvmMonitoringService"
wait_for_port 8084 "MemoryOptimizedMicroservice"
wait_for_port 8085 "StructuredMicroservice"
wait_for_port 8086 "ThreadOptimizedMicroservice"

echo ""
echo " Running Benchmarks..."
echo "======================"

./scripts/test_structured_concurrency.sh
echo ""
./scripts/advanced_structured_concurrency_test.sh
echo ""
./scripts/test_clean_structured.sh
echo ""

if command -v wrk &> /dev/null; then
    echo " Running Load Benchmarks with wrk..."
    ./scripts/benchmark.sh
    echo ""
    echo "Testing memory-intensive operations on port 8084..."
    wrk -t4 -c100 -d10s http://localhost:8084/memory-leak
    wrk -t4 -c100 -d10s http://localhost:8084/cpu-intensive
    echo ""
    echo "Testing thread-optimized operations on port 8086..."
    wrk -t4 -c100 -d10s http://localhost:8086/compute-optimized
    wrk -t4 -c100 -d10s http://localhost:8086/io-optimized
fi

echo ""
echo " All benchmarks and tests completed!"
echo "Summary results should be visible above."
