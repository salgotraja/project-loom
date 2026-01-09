package app.js.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualThreadObservability {
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadObservability.class);
    
    static void main(String[] args) throws Exception {
        logger.info(" Virtual Thread Observability & Monitoring");
        logger.info("===========================================");

        VirtualThreadMonitor monitor = new VirtualThreadMonitor();
        monitor.startMonitoring();

        logger.info("\n Test 1: Basic Virtual Thread Metrics");
        testBasicMetrics(monitor);

        logger.info("\n Test 2: Structured Concurrency Metrics");
        testStructuredConcurrencyMetrics(monitor);

        logger.info("\n Test 3: Performance Profiling");
        testPerformanceProfiling(monitor);

        logger.info("\n Test 4: Resource Usage Analysis");
        testResourceUsageAnalysis(monitor);

        logger.info("\n Test 5: Error Tracking");
        testErrorTracking(monitor);

        Thread.sleep(2000);
        logger.info("\n Final Monitoring Report:");
        monitor.printDetailedReport();

        monitor.stopMonitoring();
    }

    private static void testBasicMetrics(VirtualThreadMonitor monitor) throws Exception {
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            final int taskId = i;
            Thread thread = Thread.startVirtualThread(() -> {
                try {
                    monitor.trackTask("basic-task-" + taskId, () -> {
                        Thread.sleep(100 + ThreadLocalRandom.current().nextInt(200));
                        return "Task " + taskId + " completed";
                    });
                } catch (Exception e) {
                    logger.info("Error occurred in task {}: {}", taskId, e.getMessage());
                }
            });
            threads.add(thread);
        }

        for (Thread thread : threads) {
            thread.join();
        }

        logger.info("   Basic metrics test completed");
    }

    private static void testStructuredConcurrencyMetrics(VirtualThreadMonitor monitor) throws Exception {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            for (int i = 0; i < 20; i++) {
                final int taskId = i;
                scope.fork(() -> monitor.trackTask("structured-task-" + taskId, () -> {
                    Thread.sleep(50 + ThreadLocalRandom.current().nextInt(100));
                    return "Structured task " + taskId + " completed";
                }));
            }

            scope.join();
            scope.throwIfFailed();
        }

        logger.info("   Structured concurrency metrics test completed");
    }

    private static void testPerformanceProfiling(VirtualThreadMonitor monitor) throws Exception {
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    monitor.trackTask("cpu-intensive-" + taskId, () -> {
                        long result = 0;
                        for (int j = 0; j < 100_000; j++) {
                            result += Math.sin(j);
                        }
                        Thread.sleep(10);
                        return "CPU task " + taskId + " result: " + result;
                    });
                } catch (Exception e) {
                    logger.error("Error occurred in task {}: {}", taskId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        logger.info("   Performance profiling test completed");
    }

    private static void testResourceUsageAnalysis(VirtualThreadMonitor monitor) throws Exception {
        CountDownLatch latch = new CountDownLatch(15);

        for (int i = 0; i < 15; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    monitor.trackTask("memory-intensive-" + taskId, () -> {
                        List<byte[]> memory = new ArrayList<>();
                        for (int j = 0; j < 100; j++) {
                            memory.add(new byte[1024]);
                        }
                        Thread.sleep(100);
                        return "Memory task " + taskId + " allocated " + memory.size() + " blocks";
                    });
                } catch (Exception e) {
                    logger.error("Error occurred in task - {}: {}", taskId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        logger.info("   Resource usage analysis test completed");
    }

    private static void testErrorTracking(VirtualThreadMonitor monitor) throws Exception {

        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            Thread.startVirtualThread(() -> {
                try {
                    monitor.trackTask("error-prone-" + taskId, () -> {
                        Thread.sleep(50);
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            throw new RuntimeException("Simulated error in task " + taskId);
                        }
                        return "Success task " + taskId;
                    });
                } catch (Exception e) {
                    logger.error("Error occurred in task  {}: {}", taskId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        logger.info("   Error tracking test completed");
    }

    private static class VirtualThreadMonitor {
        private final AtomicLong taskCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicReference<Instant> monitoringStartTime = new AtomicReference<>();

        private final Map<String, TaskMetrics> taskMetrics = new ConcurrentHashMap<>();
        private final Map<String, Long> errorCounts = new ConcurrentHashMap<>();

        private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        private final Runtime runtime = Runtime.getRuntime();

        private ScheduledExecutorService scheduler;
        private volatile boolean monitoring = false;

        public void startMonitoring() {
            monitoringStartTime.set(Instant.now());
            monitoring = true;

            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::collectMetrics, 0, 1, TimeUnit.SECONDS);

            logger.info(" Monitoring started");
        }

        public void stopMonitoring() {
            monitoring = false;
            if (scheduler != null) {
                scheduler.shutdown();
            }
            logger.info(" Monitoring stopped");
        }

        public <T> T trackTask(String taskName, Callable<T> task) throws Exception {
            if (!monitoring) return task.call();

            taskCount.incrementAndGet();
            long startTime = System.nanoTime();
            long startMemory = runtime.totalMemory() - runtime.freeMemory();

            try {
                T result = task.call();

                long executionTime = System.nanoTime() - startTime;
                long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) - startMemory;

                successCount.incrementAndGet();
                totalExecutionTime.addAndGet(executionTime);

                taskMetrics.compute(taskName, (key, metrics) -> {
                    if (metrics == null) {
                        metrics = new TaskMetrics();
                    }
                    metrics.addExecution(executionTime, memoryUsed, true);
                    return metrics;
                });

                return result;

            } catch (Exception e) {
                long executionTime = System.nanoTime() - startTime;

                errorCount.incrementAndGet();
                totalExecutionTime.addAndGet(executionTime);

                errorCounts.merge(e.getClass().getSimpleName(), 1L, Long::sum);

                taskMetrics.compute(taskName, (key, metrics) -> {
                    if (metrics == null) {
                        metrics = new TaskMetrics();
                    }
                    metrics.addExecution(executionTime, 0, false);
                    return metrics;
                });

                throw e;
            }
        }

        private void collectMetrics() {
            if (!monitoring) return;

            long currentTime = System.currentTimeMillis();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            int threadCount = threadBean.getThreadCount();

            System.out.printf("[%tT] Threads: %d, Memory: %.2fMB, Tasks: %d (Success: %d, Errors: %d)%n",
                             currentTime, threadCount, usedMemory / 1024.0 / 1024.0,
                             taskCount.get(), successCount.get(), errorCount.get());
        }

        public void printDetailedReport() {
            Duration totalTime = Duration.between(monitoringStartTime.get(), Instant.now());

            logger.info("========================================");
            logger.info(" DETAILED MONITORING REPORT");
            logger.info("========================================");

            System.out.printf("Monitoring Duration: %s%n", formatDuration(totalTime));
            System.out.printf("Total Tasks: %d%n", taskCount.get());
            System.out.printf("Successful Tasks: %d (%.1f%%)%n",
                             successCount.get(),
                             (successCount.get() * 100.0) / taskCount.get());
            System.out.printf("Failed Tasks: %d (%.1f%%)%n",
                             errorCount.get(),
                             (errorCount.get() * 100.0) / taskCount.get());

            if (taskCount.get() > 0) {
                System.out.printf("Average Execution Time: %.2fms%n",
                                 (totalExecutionTime.get() / 1_000_000.0) / taskCount.get());
            }

            System.out.printf("Tasks per Second: %.2f%n",
                             taskCount.get() / (totalTime.toMillis() / 1000.0));

            logger.info("\n Task Breakdown:");
            taskMetrics.entrySet().stream()
                .sorted(Map.Entry.<String, TaskMetrics>comparingByValue(
                    (a, b) -> Long.compare(b.getExecutionCount(), a.getExecutionCount())))
                .forEach(entry -> {
                    String taskName = entry.getKey();
                    TaskMetrics metrics = entry.getValue();

                    System.out.printf("  %s: %d executions, avg: %.2fms, success: %.1f%%",
                                     taskName,
                                     metrics.getExecutionCount(),
                                     metrics.getAverageExecutionTime() / 1_000_000.0,
                                     metrics.getSuccessRate() * 100.0);

                    if (metrics.getTotalMemoryUsed() > 0) {
                        System.out.printf(", memory: %.2fKB",
                                         metrics.getTotalMemoryUsed() / 1024.0);
                    }
                    
                });

            if (!errorCounts.isEmpty()) {
                logger.info("\n Error Breakdown:");
                errorCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> System.out.printf("  %s: %d occurrences%n",
                                     entry.getKey(), entry.getValue()));
            }

            logger.info("\n Current Memory Usage:");
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            System.out.printf("  Total: %.2fMB, Used: %.2fMB, Free: %.2fMB%n",
                             totalMemory / 1024.0 / 1024.0,
                             usedMemory / 1024.0 / 1024.0,
                             freeMemory / 1024.0 / 1024.0);

            System.out.printf("  Memory Usage: %.1f%%%n",
                             (usedMemory * 100.0) / totalMemory);

            logger.info("\n Thread Information:");
            System.out.printf("  Current Thread Count: %d%n", threadBean.getThreadCount());
            System.out.printf("  Peak Thread Count: %d%n", threadBean.getPeakThreadCount());
            System.out.printf("  Total Started Threads: %d%n", threadBean.getTotalStartedThreadCount());

            logger.info("========================================");
        }
        
        private String formatDuration(Duration duration) {
            long seconds = duration.getSeconds();
            if (seconds < 60) {
                return seconds + "s";
            } else if (seconds < 3600) {
                return (seconds / 60) + "m " + (seconds % 60) + "s";
            } else {
                return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
            }
        }
    }
    
    private static class TaskMetrics {
        private long executionCount = 0;
        private long successCount = 0;
        private long totalExecutionTime = 0;
        private long totalMemoryUsed = 0;
        
        public void addExecution(long executionTime, long memoryUsed, boolean success) {
            executionCount++;
            totalExecutionTime += executionTime;
            totalMemoryUsed += Math.max(0, memoryUsed);
            
            if (success) {
                successCount++;
            }
        }
        
        public long getExecutionCount() { return executionCount; }
        public double getAverageExecutionTime() { 
            return executionCount > 0 ? (double) totalExecutionTime / executionCount : 0;
        }
        public double getSuccessRate() { 
            return executionCount > 0 ? (double) successCount / executionCount : 0;
        }
        public long getTotalMemoryUsed() { return totalMemoryUsed; }
    }
}