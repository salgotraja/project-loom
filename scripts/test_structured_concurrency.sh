#!/bin/bash

echo " Testing Structured Concurrency Endpoints"
echo "=============================================="

echo "Make sure VirtualThreadMicroservice is running on port 8080"
echo ""

echo "1 Testing basic endpoints:"
curl -s http://localhost:8080/block | head -1
curl -s http://localhost:8080/file | head -1
echo ""

echo "2 Testing StructuredTaskScope aggregate:"
curl -s http://localhost:8080/aggregate | head -1
echo ""

echo "3 Testing first success:"
curl -s http://localhost:8080/first-success | head -1
echo ""

echo "4 Testing aggregate with fallback:"
curl -s http://localhost:8080/aggregate-with-fallback | head -1
echo ""

echo "5 Testing multi-service aggregation:"
curl -s http://localhost:8080/multi-aggregate | head -1
echo ""

echo "6 Performance testing with wrk:"
echo "wrk -t4 -c100 -d10s http://localhost:8080/aggregate"
echo "wrk -t4 -c100 -d10s http://localhost:8080/first-success"
echo "wrk -t4 -c100 -d10s http://localhost:8080/multi-aggregate"
