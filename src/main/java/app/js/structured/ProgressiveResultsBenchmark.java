package app.js.structured;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ProgressiveResultsBenchmark {

    private static final int CONCURRENT_USERS = 100;
    private static final int TEST_DURATION_SECONDS = 30;
    private static final int WARMUP_ITERATIONS = 50;

    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong successfulRequests = new AtomicLong(0);
    private static final AtomicLong totalLatency = new AtomicLong(0);
    private static final AtomicLong progressUpdateCount = new AtomicLong(0);
    private static final Map<String, AtomicLong> taskTypeMetrics = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println(" Progressive Results & Hierarchical Task Management Benchmark");
        System.out.println("================================================================");
        System.out.printf("Duration: %d seconds, Concurrent Users: %d%n", TEST_DURATION_SECONDS, CONCURRENT_USERS);

        initializeMetrics();

        System.out.println(" Warming up JVM...");
        warmUp();

        System.out.println("\n Running benchmark scenarios...");

        BenchmarkResult traditional = runTraditionalApproach();

        BenchmarkResult progressive = runProgressiveResults();

        BenchmarkResult hierarchical = runHierarchicalTaskManagement();

        BenchmarkResult combined = runCombinedApproach();

        printDetailedResults(traditional, progressive, hierarchical, combined);

        performMemoryAnalysis();

        System.out.println("\n Benchmark completed!");
    }

    private static void initializeMetrics() {
        taskTypeMetrics.put("profile", new AtomicLong(0));
        taskTypeMetrics.put("inventory", new AtomicLong(0));
        taskTypeMetrics.put("payment", new AtomicLong(0));
        taskTypeMetrics.put("shipping", new AtomicLong(0));
        taskTypeMetrics.put("confirmation", new AtomicLong(0));
    }

    private static void warmUp() throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            simulateEcommerceWorkflow("warmup", null);
            Thread.sleep(1);
        }
        System.gc();
        Thread.sleep(1000);
        System.out.println(" Warmup completed");
    }

    private static BenchmarkResult runTraditionalApproach() throws Exception {
        System.out.println("\n Testing Traditional Blocking Approach...");
        resetMetrics();

        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);

        Thread progressThread = getThread(startTime, "   Traditional - Elapsed: %ds, Requests: %d, Success: %d%n");

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    long endTime = startTime + (TEST_DURATION_SECONDS * 1000);
                    while (System.currentTimeMillis() < endTime) {
                        long requestStart = System.currentTimeMillis();
                        try {
                            simulateTraditionalEcommerceWorkflow("user-" + userId);
                            successfulRequests.incrementAndGet();
                        } catch (Exception e) {

                        } finally {
                            totalLatency.addAndGet(System.currentTimeMillis() - requestStart);
                            totalRequests.incrementAndGet();
                        }

                        Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        progressThread.interrupt();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf(" Traditional completed in %ds%n", duration / 1000);
        return createBenchmarkResult("Traditional", duration);
    }

    private static BenchmarkResult runProgressiveResults() throws Exception {
        System.out.println("\n Testing Progressive Results Approach...");
        resetMetrics();

        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);

        Thread progressThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000);
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.printf("   Progressive - Elapsed: %ds, Requests: %d, Success: %d, Progress Updates: %d%n",
                                    elapsed, totalRequests.get(), successfulRequests.get(), progressUpdateCount.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        progressThread.start();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    long endTime = startTime + (TEST_DURATION_SECONDS * 1000);
                    while (System.currentTimeMillis() < endTime) {
                        long requestStart = System.currentTimeMillis();
                        try {
                            simulateProgressiveEcommerceWorkflow("user-" + userId,
                                    (progress) -> progressUpdateCount.incrementAndGet());
                            successfulRequests.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Progressive error: " + e.getMessage());
                        } finally {
                            totalLatency.addAndGet(System.currentTimeMillis() - requestStart);
                            totalRequests.incrementAndGet();
                        }

                        Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        progressThread.interrupt();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf(" Progressive completed in %ds%n", duration / 1000);
        return createBenchmarkResult("Progressive", duration);
    }

    private static BenchmarkResult runHierarchicalTaskManagement() throws Exception {
        System.out.println("\n Testing Hierarchical Task Management...");
        resetMetrics();

        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);

        Thread progressThread = getThread(startTime, "   Hierarchical - Elapsed: %ds, Requests: %d, Success: %d%n");

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    long endTime = startTime + (TEST_DURATION_SECONDS * 1000);
                    while (System.currentTimeMillis() < endTime) {
                        long requestStart = System.currentTimeMillis();
                        try {
                            simulateHierarchicalEcommerceWorkflow("user-" + userId);
                            successfulRequests.incrementAndGet();
                        } catch (Exception e) {

                        } finally {
                            totalLatency.addAndGet(System.currentTimeMillis() - requestStart);
                            totalRequests.incrementAndGet();
                        }

                        Thread.sleep(ThreadLocalRandom.current().nextInt(30, 150));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        progressThread.interrupt();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf(" Hierarchical completed in %ds%n", duration / 1000);
        return createBenchmarkResult("Hierarchical", duration);
    }

    private static Thread getThread(long startTime, String format) {
        Thread progressThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000);
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.printf(format,
                            elapsed, totalRequests.get(), successfulRequests.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        progressThread.start();
        return progressThread;
    }

    private static BenchmarkResult runCombinedApproach() throws Exception {
        System.out.println("\n Testing Combined Approach (Progressive + Hierarchical)...");
        resetMetrics();

        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);

        Thread progressThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000);
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.printf("   Combined - Elapsed: %ds, Requests: %d, Success: %d, Progress Updates: %d%n",
                                    elapsed, totalRequests.get(), successfulRequests.get(), progressUpdateCount.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        progressThread.start();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    long endTime = startTime + (TEST_DURATION_SECONDS * 1000);
                    while (System.currentTimeMillis() < endTime) {
                        long requestStart = System.currentTimeMillis();
                        try {
                            simulateCombinedEcommerceWorkflow("user-" + userId,
                                    (progress) -> progressUpdateCount.incrementAndGet());
                            successfulRequests.incrementAndGet();
                        } catch (Exception e) {

                        } finally {
                            totalLatency.addAndGet(System.currentTimeMillis() - requestStart);
                            totalRequests.incrementAndGet();
                        }

                        Thread.sleep(ThreadLocalRandom.current().nextInt(20, 100));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        progressThread.interrupt();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf(" Combined completed in %ds%n", duration / 1000);
        return createBenchmarkResult("Combined", duration);
    }

    private static String simulateTraditionalEcommerceWorkflow(String userId) throws Exception {

        String profile = fetchUserProfile(userId);
        Thread.sleep(50);

        String inventory = checkInventory(userId, profile);
        Thread.sleep(75);

        String payment = processPayment(userId, profile);
        Thread.sleep(100);

        String shipping = calculateShipping(userId, profile, inventory);
        Thread.sleep(80);

        String confirmation = createOrderConfirmation(userId, profile, inventory, payment, shipping);
        Thread.sleep(30);

        return confirmation;
    }

    private static String simulateProgressiveEcommerceWorkflow(String userId, Consumer<String> progressCallback) throws Exception {
        ProgressiveResults<String> progressive = new ProgressiveResults<>();

        List<Callable<String>> tasks = List.of(
                () -> {
                    String result = fetchUserProfile(userId);
                    progressCallback.accept("Profile loaded");
                    return result;
                },
                () -> {
                    String result = checkInventory(userId, "profile");
                    progressCallback.accept("Inventory checked");
                    return result;
                },
                () -> {
                    String result = processPayment(userId, "profile");
                    progressCallback.accept("Payment processed");
                    return result;
                },
                () -> {
                    String result = calculateShipping(userId, "profile", "inventory");
                    progressCallback.accept("Shipping calculated");
                    return result;
                }
        );

        final List<String> results = new ArrayList<>();
        progressive.executeWithProgressCallback(tasks, (index, result) -> {
            results.add(result);
            progressCallback.accept("Task " + index + " completed");
        });

        return createOrderConfirmation(userId, String.join(",", results), "", "", "");
    }

    private static String simulateHierarchicalEcommerceWorkflow(String userId) throws Exception {
        HierarchicalTaskManager manager = new HierarchicalTaskManager();
        return manager.executeEcommerceWorkflow(userId);
    }

    private static String simulateCombinedEcommerceWorkflow(String userId, Consumer<String> progressCallback) throws Exception {
        CombinedTaskManager manager = new CombinedTaskManager();
        return manager.executeWithProgressAndHierarchy(userId, progressCallback);
    }

    private static String fetchUserProfile(String userId) throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(20, 50));
        taskTypeMetrics.get("profile").incrementAndGet();
        return "Profile-" + userId;
    }

    private static String checkInventory(String userId, String profile) throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(30, 70));
        taskTypeMetrics.get("inventory").incrementAndGet();
        return "Inventory-" + userId;
    }

    private static String processPayment(String userId, String profile) throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(50, 120));
        taskTypeMetrics.get("payment").incrementAndGet();
        return "Payment-" + userId;
    }

    private static String calculateShipping(String userId, String profile, String inventory) throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(40, 100));
        taskTypeMetrics.get("shipping").incrementAndGet();
        return "Shipping-" + userId;
    }

    private static String createOrderConfirmation(String userId, String profile, String inventory, String payment, String shipping) throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(20, 40));
        taskTypeMetrics.get("confirmation").incrementAndGet();
        return "Order-" + userId + "-Confirmed";
    }

    private static String simulateEcommerceWorkflow(String userId, Consumer<String> progressCallback) throws Exception {
        return fetchUserProfile(userId) + "-" + checkInventory(userId, "") + "-Complete";
    }

    private static void resetMetrics() {
        totalRequests.set(0);
        successfulRequests.set(0);
        totalLatency.set(0);
        progressUpdateCount.set(0);
        taskTypeMetrics.values().forEach(counter -> counter.set(0));
    }

    private static BenchmarkResult createBenchmarkResult(String approach, long durationMs) {
        long requests = totalRequests.get();
        long successful = successfulRequests.get();
        long latency = requests > 0 ? totalLatency.get() / requests : 0;
        long throughput = requests * 1000 / durationMs;
        long progressUpdates = progressUpdateCount.get();

        return new BenchmarkResult(
                approach, requests, successful, latency, throughput, progressUpdates, durationMs
        );
    }

    private static void printDetailedResults(BenchmarkResult... results) {
        System.out.println("\n DETAILED BENCHMARK RESULTS");
        System.out.println("=" .repeat(100));

        System.out.printf("%-12s | %-10s | %-12s | %-10s | %-12s | %-15s | %-10s%n",
                "Approach", "Requests", "Success Rate", "Avg Latency", "Throughput", "Progress Updates", "Duration");
        System.out.println("-".repeat(100));

        for (BenchmarkResult result : results) {
            double successRate = result.totalRequests > 0 ? (double) result.successful / result.totalRequests * 100 : 0;
            System.out.printf("%-12s | %-10d | %-11.1f%% | %-9dms | %-11d/s | %-15d | %-9ds%n",
                    result.approach, result.totalRequests, successRate, result.avgLatency,
                    result.throughput, result.progressUpdates, result.durationMs / 1000);
        }

        if (results.length >= 2) {
            System.out.println("\n IMPROVEMENT ANALYSIS");
            BenchmarkResult baseline = results[0];

            for (int i = 1; i < results.length; i++) {
                BenchmarkResult current = results[i];
                double throughputImprovement = baseline.throughput > 0 ? 
                    ((double) current.throughput - baseline.throughput) / baseline.throughput * 100 : 0;
                double latencyImprovement = baseline.avgLatency > 0 ? 
                    ((double) baseline.avgLatency - current.avgLatency) / baseline.avgLatency * 100 : 0;

                System.out.printf("%s vs %s: Throughput %+.1f%%, Latency %+.1f%%, Progress Updates: %d%n",
                        current.approach, baseline.approach, throughputImprovement, latencyImprovement, current.progressUpdates);
            }
        }

        System.out.println("\n TASK TYPE METRICS");
        taskTypeMetrics.forEach((type, count) ->
                System.out.printf("%s tasks: %d%n", type, count.get()));
    }

    private static void performMemoryAnalysis() {
        System.out.println("\n MEMORY ANALYSIS");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        System.out.printf("Total Memory: %d MB%n", totalMemory / (1024 * 1024));
        System.out.printf("Used Memory: %d MB%n", usedMemory / (1024 * 1024));
        System.out.printf("Free Memory: %d MB%n", freeMemory / (1024 * 1024));
        System.out.printf("Max Memory: %d MB%n", runtime.maxMemory() / (1024 * 1024));
    }

    private static class BenchmarkResult {
        final String approach;
        final long totalRequests;
        final long successful;
        final long avgLatency;
        final long throughput;
        final long progressUpdates;
        final long durationMs;

        BenchmarkResult(String approach, long totalRequests, long successful,
                        long avgLatency, long throughput, long progressUpdates, long durationMs) {
            this.approach = approach;
            this.totalRequests = totalRequests;
            this.successful = successful;
            this.avgLatency = avgLatency;
            this.throughput = throughput;
            this.progressUpdates = progressUpdates;
            this.durationMs = durationMs;
        }
    }

    private static class ProgressiveResults<T> {

        @FunctionalInterface
        public interface ProgressCallback<T> {
            void onProgress(int taskIndex, T result);
        }

        public void executeWithProgressCallback(List<Callable<T>> tasks,
                                                ProgressCallback<T> callback)
                throws InterruptedException {

            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();

                for (Callable<T> task : tasks) {
                    subtasks.add(scope.fork(task));
                }

                boolean[] completed = new boolean[tasks.size()];
                int totalCompleted = 0;
                long startTime = System.currentTimeMillis();
                long timeout = 10000;

                while (totalCompleted < tasks.size() && 
                       (System.currentTimeMillis() - startTime) < timeout) {
                    
                    try {
                        scope.joinUntil(Instant.now().plusMillis(10));
                    } catch (TimeoutException e) {

                    }

                    for (int i = 0; i < subtasks.size(); i++) {
                        if (!completed[i] &&
                                subtasks.get(i).state() == StructuredTaskScope.Subtask.State.SUCCESS) {

                            completed[i] = true;
                            totalCompleted++;
                            try {
                                callback.onProgress(i, subtasks.get(i).get());
                            } catch (Exception e) {

                            }
                        } else if (!completed[i] &&
                                subtasks.get(i).state() == StructuredTaskScope.Subtask.State.FAILED) {
                            completed[i] = true;
                            totalCompleted++;
                        }
                    }
                }

                try {
                    scope.join();
                } catch (Exception e) {

                }
                
            } catch (Exception e) {
                throw new RuntimeException("Progressive execution timeout", e);
            }
        }
    }

    private static class HierarchicalTaskManager {

        public String executeEcommerceWorkflow(String userId) throws Exception {
            try (var parentScope = new StructuredTaskScope.ShutdownOnFailure()) {

                var userDataTask = parentScope.fork(() -> gatherUserData(userId));

                var businessLogicTask = parentScope.fork(() -> processBusinessLogic(userId));

                var finalizationTask = parentScope.fork(() -> finalizeOrder(userId));

                parentScope.join();

                return String.format("Hierarchical Order: [%s, %s, %s]",
                        userDataTask.get(), businessLogicTask.get(), finalizationTask.get());
            }
        }

        private String gatherUserData(String userId) throws Exception {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                var profileTask = scope.fork(() -> fetchUserProfile(userId));
                var preferencesTask = scope.fork(() -> fetchUserPreferences(userId));

                scope.join();

                return String.format("UserData[%s,%s]", profileTask.get(), preferencesTask.get());
            }
        }

        private String processBusinessLogic(String userId) throws Exception {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                var inventoryTask = scope.fork(() -> checkInventory(userId, ""));
                var paymentTask = scope.fork(() -> processPayment(userId, ""));
                var shippingTask = scope.fork(() -> calculateShipping(userId, "", ""));

                scope.join();

                return String.format("BusinessLogic[%s,%s,%s]",
                        inventoryTask.get(), paymentTask.get(), shippingTask.get());
            }
        }

        private String finalizeOrder(String userId) throws Exception {
            Thread.sleep(ThreadLocalRandom.current().nextInt(20, 40));
            return "OrderFinalized-" + userId;
        }

        private String fetchUserPreferences(String userId) throws Exception {
            Thread.sleep(ThreadLocalRandom.current().nextInt(15, 30));
            return "Preferences-" + userId;
        }
    }

    private static class CombinedTaskManager {

        public String executeWithProgressAndHierarchy(String userId, Consumer<String> progressCallback) throws Exception {
            progressCallback.accept("Starting hierarchical workflow");

            try (var parentScope = new StructuredTaskScope.ShutdownOnFailure()) {

                var level1Task = parentScope.fork(() -> {
                    progressCallback.accept("Level 1 starting");
                    String result = executeLevel1(userId, progressCallback);
                    progressCallback.accept("Level 1 completed");
                    return result;
                });

                var level2Task = parentScope.fork(() -> {
                    progressCallback.accept("Level 2 starting");
                    String result = executeLevel2(userId, progressCallback);
                    progressCallback.accept("Level 2 completed");
                    return result;
                });

                var level3Task = parentScope.fork(() -> {
                    progressCallback.accept("Level 3 starting");
                    String result = executeLevel3(userId, progressCallback);
                    progressCallback.accept("Level 3 completed");
                    return result;
                });

                parentScope.join();

                progressCallback.accept("All levels completed");

                return String.format("Combined[%s,%s,%s]",
                        level1Task.get(), level2Task.get(), level3Task.get());
            }
        }

        private String executeLevel1(String userId, Consumer<String> progressCallback) throws Exception {
            ProgressiveResults<String> progressive = new ProgressiveResults<>();

            List<Callable<String>> tasks = List.of(
                    () -> fetchUserProfile(userId),
                    () -> fetchUserPreferences(userId)
            );

            final List<String> results = new ArrayList<>();
            progressive.executeWithProgressCallback(tasks, (index, result) -> {
                results.add(result);
                progressCallback.accept("Level1-Task" + index + " completed");
            });

            return "Level1[" + String.join(",", results) + "]";
        }

        private String executeLevel2(String userId, Consumer<String> progressCallback) throws Exception {
            ProgressiveResults<String> progressive = new ProgressiveResults<>();

            List<Callable<String>> tasks = List.of(
                    () -> checkInventory(userId, ""),
                    () -> processPayment(userId, ""),
                    () -> calculateShipping(userId, "", "")
            );

            final List<String> results = new ArrayList<>();
            progressive.executeWithProgressCallback(tasks, (index, result) -> {
                results.add(result);
                progressCallback.accept("Level2-Task" + index + " completed");
            });

            return "Level2[" + String.join(",", results) + "]";
        }

        private String executeLevel3(String userId, Consumer<String> progressCallback) throws Exception {
            Thread.sleep(ThreadLocalRandom.current().nextInt(20, 40));
            progressCallback.accept("Order finalization completed");
            return "Level3[OrderFinalized-" + userId + "]";
        }

        private String fetchUserPreferences(String userId) throws Exception {
            Thread.sleep(ThreadLocalRandom.current().nextInt(15, 30));
            return "Preferences-" + userId;
        }
    }
}