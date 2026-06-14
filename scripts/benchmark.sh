#!/bin/bash

echo "Microservice Benchmarking with wrk"
echo "====================================="

echo ""
echo "Testing Virtual Thread Server (port 8080)"
echo "============================================="

echo ""
echo "CPU-Intensive Endpoint (/compute)"
echo "wrk -t8 -c1000 -d30s http://localhost:8080/compute"
wrk -t8 -c1000 -d30s http://localhost:8080/compute

echo ""
echo "I/O Blocking Endpoint (/block)"
echo "wrk -t8 -c1000 -d30s http://localhost:8080/block"
wrk -t8 -c1000 -d30s http://localhost:8080/block

echo ""
echo "File Reading Endpoint (/file)"
echo "wrk -t8 -c1000 -d30s http://localhost:8080/file"
wrk -t8 -c1000 -d30s http://localhost:8080/file

echo ""
echo "Testing Platform Thread Server (port 8081)"
echo "=============================================="

echo ""
echo "CPU-Intensive Endpoint (/compute)"
echo "wrk -t8 -c1000 -d30s http://localhost:8081/compute"
wrk -t8 -c1000 -d30s http://localhost:8081/compute

echo ""
echo "I/O Blocking Endpoint (/block)"
echo "wrk -t8 -c1000 -d30s http://localhost:8081/block"
wrk -t8 -c1000 -d30s http://localhost:8081/block

echo ""
echo "File Reading Endpoint (/file)"
echo "wrk -t8 -c1000 -d30s http://localhost:8081/file"
wrk -t8 -c1000 -d30s http://localhost:8081/file

echo ""
echo "Benchmarking completed!"
