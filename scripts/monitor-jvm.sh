#!/bin/bash

echo "=== JVM Monitoring ==="

echo "Monitoring memory usage..."
while true; do
    curl -s http://localhost:8084/memory-stats
    echo "---"
    sleep 5
done &

echo "Monitoring thread usage..."
while true; do
    curl -s http://localhost:8086/thread-stats
    echo "---"
    sleep 5
done &

echo "Monitoring JVM metrics..."
while true; do
    curl -s http://localhost:8083/metrics
    echo "---"
    sleep 10
done