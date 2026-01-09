#!/bin/bash

echo " Structured Concurrency Microservice Testing"
echo "=============================================="

test_endpoint() {
    local endpoint=$1
    local name=$2
    
    echo "Testing $name..."
    
    echo " Single request:"
    curl -s "http://localhost:8080$endpoint" | head -1
    
    echo " Multiple requests (10 concurrent):"
    for i in {1..10}; do
        curl -s "http://localhost:8080$endpoint" &
    done
    wait
    
    echo " Performance test (100 requests):"
    time {
        for i in {1..100}; do
            curl -s "http://localhost:8080$endpoint" > /dev/null
        done
    }
    
    echo " $name test completed"
    echo ""
}

if ! curl -s http://localhost:8080/health > /dev/null; then
    echo " Server is not running on port 8080"
    echo "Please start: java app.js.microservices.VirtualThreadMicroservice"
    exit 1
fi

echo " Server is running"
echo ""

test_endpoint "/aggregate" "StructuredTaskScope Aggregate"
test_endpoint "/aggregate-old" "CompletableFuture Aggregate"
test_endpoint "/first-success" "First Success"
test_endpoint "/aggregate-with-fallback" "Aggregate with Fallback"
test_endpoint "/multi-aggregate" "Multi-service Aggregation"

echo " Load Testing with curl"
echo "=========================="

echo "Load testing /aggregate endpoint..."
time {
    for i in {1..50}; do
        curl -s http://localhost:8080/aggregate > /dev/null &
    done
    wait
}

echo "Load testing /aggregate-old endpoint..."
time {
    for i in {1..50}; do
        curl -s http://localhost:8080/aggregate-old > /dev/null &
    done
    wait
}

echo " All tests completed!"