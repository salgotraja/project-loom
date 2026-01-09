#!/bin/bash

JAVA_OPTS="-Xms1g -Xmx2g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=20 \
-XX:+FlightRecorder \
-XX:StartFlightRecording=duration=300s,filename=thread-optimized.jfr \
-XX:+UnlockExperimentalVMOptions \
-Djdk.virtualThreadScheduler.parallelism=32 \
-Djdk.virtualThreadScheduler.maxPoolSize=256"

if [ -z "$CP" ]; then
    CP=$(mvn dependency:build-classpath | grep -v '\[INFO\]' | grep -v '\[WARNING\]' | tr '\n' ' '):target/classes
fi

echo "Starting Thread-Optimized Microservice with JVM tuning..."
java --enable-preview $JAVA_OPTS -cp "$CP" app.js.threads.ThreadOptimizedMicroservice