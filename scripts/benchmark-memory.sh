#!/bin/bash

echo "=== Memory Optimization Benchmarking ==="

echo "Starting services..."
./scripts/run-memory-optimized.sh &
MEMORY_PID=$!

./scripts/run-thread-optimized.sh &
THREAD_PID=$!

sleep 10

echo "Testing memory leak detection..."
wrk -t4 -c100 -d30s http://localhost:8084/memory-leak

echo "Testing CPU-intensive operations..."
wrk -t4 -c100 -d30s http://localhost:8084/cpu-intensive

echo "Testing file I/O operations..."
wrk -t4 -c100 -d30s http://localhost:8084/file-io

echo "Memory statistics:"
curl http://localhost:8084/memory-stats

echo "Testing thread-optimized compute..."
wrk -t4 -c100 -d30s http://localhost:8086/compute-optimized

echo "Testing thread-optimized I/O..."
wrk -t4 -c100 -d30s http://localhost:8086/io-optimized

echo "Thread statistics:"
curl http://localhost:8086/thread-stats

kill $MEMORY_PID $THREAD_PID
