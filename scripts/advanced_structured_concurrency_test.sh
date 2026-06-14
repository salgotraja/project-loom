#!/bin/bash

echo "Advanced Structured Concurrency Testing"
echo "==========================================="

if ! curl -s http://localhost:8082/health > /dev/null; then
    echo "Server is not running on port 8082"
    echo "Please start: java app.js.microservices.AdvancedStructuredConcurrencyMicroservice"
    exit 1
fi

echo "Server is running on port 8082"
echo ""

endpoints=(
    "/structured/block:Structured DB Call"
    "/structured/file:Structured File Operation"
    "/structured/aggregate:Structured Service Aggregation"
    "/timeout/short:Short Timeout Example"
    "/timeout/graceful:Graceful Timeout Example"
    "/deadline/strict:Strict Deadline Example"
    "/async/multi-service:Async Multi-Service Call"
    "/async/failover:Async Failover Call"
    "/async/race:Async Race Condition"
    "/service/pipeline:Service Pipeline"
    "/service/circuit-breaker:Circuit Breaker Pattern"
    "/service/retry:Retry Pattern"
    "/pattern/scatter-gather:Scatter-Gather Pattern"
    "/pattern/bulkhead:Bulkhead Pattern"
)

for endpoint_info in "${endpoints[@]}"; do
    IFS=':' read -r endpoint name <<< "$endpoint_info"

    echo "Testing $name"
    echo "   Endpoint: $endpoint"

    response=$(curl -s "http://localhost:8082$endpoint")
    echo "   Response: ${response:0:100}..."

    echo "   Performance test (10 requests):"
    time {
        for i in {1..10}; do
            curl -s "http://localhost:8082$endpoint" > /dev/null &
        done
        wait
    }

    echo "   $name completed"
    echo ""
done

echo "Final metrics:"
curl -s http://localhost:8082/metrics

echo ""
echo "All tests completed!"
