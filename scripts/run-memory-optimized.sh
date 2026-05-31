#!/bin/bash

JAVA_OPTS="-Xms1g -Xmx2g \
-XX:NewRatio=2 \
-XX:MaxMetaspaceSize=512m \
-XX:+UseZGC \
-XX:MaxGCPauseMillis=10 \
-XX:+FlightRecorder \
-XX:StartFlightRecording=duration=300s,filename=memory-optimized.jfr \
-XX:+UnlockExperimentalVMOptions \
-Djava.util.concurrent.ForkJoinPool.common.parallelism=8"

if [ "$(uname -s)" = "Linux" ]; then
    JAVA_OPTS="$JAVA_OPTS -XX:+UseTransparentHugePages"
fi

if [ -z "$CP" ]; then
    mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
    CP="$(cat cp.txt):target/classes"
fi

echo "Starting Memory-Optimized Microservice with JVM tuning..."
java --enable-preview $JAVA_OPTS -cp "$CP" app.js.memory.MemoryOptimizedMicroservice
