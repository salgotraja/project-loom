#!/bin/bash

echo " Testing Clean Structured Concurrency Microservice"
echo "===================================================="

if ! curl -s http://localhost:8085/health > /dev/null; then
    echo " Server is not running on port 8085"
    echo "Please start: java app.js.structured.StructuredMicroservice"
    exit 1
fi

echo " Server is running on port 8085"
echo ""

echo " Testing User Profile (parallel data fetching):"
curl -s "http://localhost:8085/user/profile?userId=john123"
echo -e "\n"

echo " Testing User Dashboard (three-way parallel):"
curl -s "http://localhost:8085/user/dashboard?userId=john123"
echo -e "\n"

echo " Testing Service Aggregation:"
curl -s "http://localhost:8085/services/aggregate"
echo -e "\n"

echo " Testing Cached Data (first success):"
curl -s "http://localhost:8085/cache/data?key=userdata"
echo -e "\n"

echo " Testing Data with Fallback:"
curl -s "http://localhost:8085/data/with-fallback?key=userdata"
echo -e "\n"

echo " Testing Retry Operation:"
curl -s "http://localhost:8085/retry/operation?op=important-task"
echo -e "\n"

echo " Testing Protected Service (circuit breaker):"
curl -s "http://localhost:8085/protected/service?req=test-request"
echo -e "\n"

echo " Testing Timed Operation:"
curl -s "http://localhost:8085/timed/operation?op=slow-task"
echo -e "\n"

echo " Testing Order Processing (complex workflow):"
curl -s "http://localhost:8085/order/process?orderId=ORD-123"
echo -e "\n"

echo " All tests completed!"