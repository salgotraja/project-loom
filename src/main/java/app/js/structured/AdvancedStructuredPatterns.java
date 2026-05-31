package app.js.structured;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.IntStream;
import java.util.Collections;

import static java.util.stream.Collectors.toList;

import java.util.Objects;

public class AdvancedStructuredPatterns {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedStructuredPatterns.class);
    
    static void main(String[] args) throws Exception {
        logger.info(" Advanced Structured Concurrency Patterns");
        logger.info("===========================================");

        logger.info("\n Pattern 1: Timeout with Partial Results");
        testTimeoutWithPartialResults();

        logger.info("\n Pattern 2: Conditional Cancellation");
        testConditionalCancellation();

        logger.info("\n Pattern 3: Progressive Results");
        testProgressiveResults();

        logger.info("\n Pattern 4: Hierarchical Task Management");
        testHierarchicalTaskManagement();

        logger.info("\n Pattern 5: Resource-aware Scheduling");
        testResourceAwareScheduling();

        logger.info("\n Pattern 6: Adaptive Concurrency");
        testAdaptiveConcurrency();

        logger.info("\n Pattern 7: Bulkhead Pattern");
        testBulkheadPattern();
        
        logger.info("\n All advanced patterns completed!");
    }

    private static void testTimeoutWithPartialResults() throws Exception {
        TimeoutWithPartialResults<String> pattern = new TimeoutWithPartialResults<>();
        
        List<Callable<String>> tasks = List.of(
            () -> { Thread.sleep(100); return "Quick result"; },
            () -> { Thread.sleep(500); return "Medium result"; },
            () -> { Thread.sleep(1000); return "Slow result"; },
            () -> { Thread.sleep(2000); return "Very slow result"; }
        );
        
        logger.info("   Starting timeout test with 600ms limit...");
        
        try {
            var result = pattern.executeWithTimeout(tasks, Duration.ofMillis(600));
            
            System.out.printf("   Completed: %d/%d tasks%n", 
                         result.getCompletedResults().size(), tasks.size());
            System.out.printf("   Results: %s%n", result.getCompletedResults());
            System.out.printf("   Timed out: %s%n", result.getTimedOut());
            
            if (result.hasTimeout()) {
                logger.info("   Some tasks exceeded the timeout (this is expected)");
            }
            
        } catch (Exception e) {
            System.err.println("   Unexpected error: " + e.getMessage());
            throw e;
        }
    }

    private static void testConditionalCancellation() throws Exception {
        ConditionalCancellation<String> pattern = new ConditionalCancellation<>();
        
        List<Callable<String>> tasks = List.of(
            () -> { Thread.sleep(100); return "success"; },
            () -> { Thread.sleep(200); return "error"; },
            () -> { Thread.sleep(300); return "success"; },
            () -> { Thread.sleep(400); return "success"; }
        );

        var result = pattern.executeWithCondition(tasks, 
            results -> {
                boolean hasError = results.stream().anyMatch(r -> "error".equals(r));
                if (hasError) {
                    System.out.printf("   Cancellation triggered! Found error in results: %s%n", results);
                }
                return hasError;
            });
        
        System.out.printf("   Conditional Cancellation Results:%n");
        System.out.printf("    - Completed results: %s%n", result.getCompletedResults());
        System.out.printf("    - Cancelled task indices: %s%n", result.getCancelledTasks());
        System.out.printf("    - Was cancelled: %s%n", result.wasCancelled());
        System.out.printf("    - Reason: %s%n", result.getCancellationReason());
        System.out.printf("    - Execution time: %d ms%n", result.getExecutionTimeMs());
        
        if (result.wasCancelled()) {
            System.out.printf("     Successfully cancelled due to: %s%n", result.getCancellationReason());
        } else {
            System.out.printf("     All tasks completed without cancellation%n");
        }
    }

    private static void testProgressiveResults() throws Exception {
        ProgressiveResults<String> pattern = new ProgressiveResults<>();
        
        List<Callable<String>> tasks = List.of(
            () -> { Thread.sleep(100); return "Result 1"; },
            () -> { Thread.sleep(200); return "Result 2"; },
            () -> { Thread.sleep(150); return "Result 3"; },
            () -> { Thread.sleep(250); return "Result 4"; }
        );
        
        System.out.printf("   Starting progressive execution of %d tasks...%n", tasks.size());
        
        var summary = pattern.executeWithProgressCallback(tasks, 
            (index, result) -> System.out.printf("   Task %d completed: %s (at %d ms)%n", 
                index, result, System.currentTimeMillis() % 10000));
        
        System.out.printf("   Progressive Results Summary:%n");
        System.out.printf("    - Completion rate: %.1f%% (%d/%d tasks)%n", 
            summary.getCompletionRate() * 100, summary.getCompletedCount(), summary.getTotalCount());
        System.out.printf("    - Total execution time: %d ms%n", summary.getExecutionTimeMs());
        System.out.printf("    - Results: %s%n", summary.getCompletedResults());
        System.out.printf("    - Errors: %d%n", summary.getErrors().size());
        
        if (summary.isTimedOut()) {
            System.out.printf("     Execution timed out%n");
        } else {
            System.out.printf("     All tasks completed successfully%n");
        }
    }

    private static void testHierarchicalTaskManagement() throws Exception {
        HierarchicalTaskManager manager = new HierarchicalTaskManager();
        
        var result = manager.executeHierarchical();
        System.out.printf("  Hierarchical result: %s%n", result);
    }

    private static void testResourceAwareScheduling() throws Exception {
        ResourceAwareScheduler scheduler = new ResourceAwareScheduler();
        
        List<ResourceTask> tasks = List.of(
            new ResourceTask("CPU-1", ResourceType.CPU, 2),
            new ResourceTask("CPU-2", ResourceType.CPU, 3),
            new ResourceTask("MEM-1", ResourceType.MEMORY, 1),
            new ResourceTask("MEM-2", ResourceType.MEMORY, 2),
            new ResourceTask("IO-1", ResourceType.IO, 1)
        );
        
        var results = scheduler.executeResourceAware(tasks);
        System.out.printf("  Resource-aware results: %s%n", results);
    }

    private static void testAdaptiveConcurrency() throws Exception {
        AdaptiveConcurrency<String> adaptive = new AdaptiveConcurrency<>();
        
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            tasks.add(() -> {
                Thread.sleep(100 + ThreadLocalRandom.current().nextInt(200));
                return "Task " + taskId + " completed";
            });
        }
        
        var results = adaptive.executeAdaptive(tasks);
        System.out.printf("  Adaptive results: %d tasks completed%n", results.size());
    }

    private static void testBulkheadPattern() throws Exception {
        BulkheadPattern bulkhead = new BulkheadPattern();

        List<Callable<String>> criticalTasks = List.of(
            () -> { Thread.sleep(100); return "Critical-1"; },
            () -> { Thread.sleep(150); return "Critical-2"; }
        );
        
        List<Callable<String>> normalTasks = List.of(
            () -> { Thread.sleep(200); return "Normal-1"; },
            () -> { Thread.sleep(250); return "Normal-2"; },
            () -> { Thread.sleep(300); return "Normal-3"; }
        );
        
        var results = bulkhead.executeWithBulkhead(criticalTasks, normalTasks);
        System.out.printf("  Bulkhead results: %s%n", results);
    }

    private static class TimeoutWithPartialResults<T> {
        
        public static class PartialResult<T> {
            private final List<T> completedResults;
            private final List<Integer> timedOut;
            private final boolean hasTimeout;
            
            public PartialResult(List<T> completedResults, List<Integer> timedOut, boolean hasTimeout) {
                this.completedResults = completedResults;
                this.timedOut = timedOut;
                this.hasTimeout = hasTimeout;
            }
            
            public List<T> getCompletedResults() { return completedResults; }
            public List<Integer> getTimedOut() { return timedOut; }
            public boolean hasTimeout() { return hasTimeout; }
        }
        
        public PartialResult<T> executeWithTimeout(List<Callable<T>> tasks, Duration timeout) 
                throws InterruptedException {
            
            List<T> completedResults = new ArrayList<>();
            List<Integer> timedOut = new ArrayList<>();

            List<CompletableFuture<T>> futures = new ArrayList<>();
            
            for (Callable<T> task : tasks) {
                CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                
                futures.add(future);
            }

            long timeoutMillis = timeout.toMillis();
            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                boolean allDone = true;
                
                for (CompletableFuture<T> future : futures) {
                    if (!future.isDone()) {
                        allDone = false;
                        break;
                    }
                }
                
                if (allDone) {
                    logger.info("   All tasks completed before timeout");
                    break;
                }
                
                Thread.sleep(10);
            }

            boolean hasTimeout = System.currentTimeMillis() - startTime >= timeoutMillis;
            if (hasTimeout) {
                logger.info("   Timeout occurred, collecting partial results...");

                for (CompletableFuture<T> future : futures) {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                }
            }

            for (int i = 0; i < futures.size(); i++) {
                CompletableFuture<T> future = futures.get(i);
                
                if (future.isDone() && !future.isCancelled()) {
                    try {
                        completedResults.add(future.get());
                    } catch (Exception e) {
                        timedOut.add(i);
                    }
                } else {
                    timedOut.add(i);
                }
            }
            
            return new PartialResult<>(completedResults, timedOut, hasTimeout);
        }
    }

    private static class ConditionalCancellation<T> {
        
        public static class CancellationResult<T> {
            private final List<T> completedResults;
            private final List<Integer> cancelledTasks;
            private final String cancellationReason;
            private final long executionTimeMs;
            private final boolean wasCancelled;
            
            public CancellationResult(List<T> completedResults, List<Integer> cancelledTasks, 
                                String cancellationReason, long executionTimeMs) {
                this.completedResults = completedResults;
                this.cancelledTasks = cancelledTasks;
                this.cancellationReason = cancellationReason;
                this.executionTimeMs = executionTimeMs;
                this.wasCancelled = cancellationReason != null;
            }
            
            public List<T> getCompletedResults() { return completedResults; }
            public List<Integer> getCancelledTasks() { return cancelledTasks; }
            public String getCancellationReason() { return cancellationReason; }
            public long getExecutionTimeMs() { return executionTimeMs; }
            public boolean wasCancelled() { return wasCancelled; }
            
            @Override
            public String toString() {
                return String.format("CancellationResult{completed=%d, cancelled=%d, reason='%s', time=%dms}",
                    completedResults.size(), cancelledTasks.size(), cancellationReason, executionTimeMs);
            }
        }
        
        public CancellationResult<T> executeWithCondition(
            List<Callable<T>> tasks, 
            Function<List<T>, Boolean> cancelCondition) throws InterruptedException {
        
        return executeWithCondition(tasks, cancelCondition, Duration.ofMillis(50), Duration.ofSeconds(30));
    }
    
    public CancellationResult<T> executeWithCondition(
            List<Callable<T>> tasks, 
            Function<List<T>, Boolean> cancelCondition,
            Duration checkInterval,
            Duration maxDuration) throws InterruptedException {
        
        long startTime = System.currentTimeMillis();
        List<T> allResults = new ArrayList<>();
        List<Integer> cancelledTasks = new ArrayList<>();
        boolean[] completed = new boolean[tasks.size()];
        ConcurrentHashMap<Integer, T> resultsByIndex = new ConcurrentHashMap<>();
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();

            for (int i = 0; i < tasks.size(); i++) {
                int taskIndex = i;
                Callable<T> task = tasks.get(i);
                subtasks.add(scope.fork(() -> {
                    T result = task.call();
                    resultsByIndex.put(taskIndex, result);
                    return result;
                }));
            }

            Instant maxTime = Instant.now().plus(maxDuration);
            
            while (Instant.now().isBefore(maxTime)) {
                // Java 25: joinUntil removed - use sleep for polling
                try {
                    Thread.sleep(checkInterval.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while waiting for tasks to complete");
                }

                List<T> newResults = new ArrayList<>();
                Set<Integer> completedIndices = new HashSet<>();
                
                for (int i = 0; i < subtasks.size(); i++) {
                    var subtask = subtasks.get(i);
                    
                    switch (subtask.state()) {
                        case SUCCESS:
                            if (!completed[i]) {
                                newResults.add(resultsByIndex.get(i));
                                completed[i] = true;
                                completedIndices.add(i);
                            }
                            break;
                        case FAILED:
                            if (!completed[i]) {
                                cancelledTasks.add(i);
                                completed[i] = true;
                                completedIndices.add(i);
                            }
                            break;
                        case UNAVAILABLE:
                            break;
                    }
                }

                allResults.addAll(newResults);

                if (cancelCondition.apply(allResults)) {
                    // Java 25: shutdown() removed - automatic on scope close
                    // scope.shutdown();
                    logger.error("Cancellation condition met");
                    for (int i = 0; i < subtasks.size(); i++) {
                        if (!completed[i]) {
                            cancelledTasks.add(i);
                        }
                    }
                    scope.join();
                    
                    return new CancellationResult<>(
                        allResults, 
                        cancelledTasks, 
                        "Cancellation condition met", 
                        System.currentTimeMillis() - startTime
                    );
                }

                boolean allDone = subtasks.stream()
                    .allMatch(s -> s.state() != StructuredTaskScope.Subtask.State.UNAVAILABLE);
                
                if (allDone) {
                    break;
                }
            }

            scope.join();

            if (Instant.now().isAfter(maxTime)) {
                // Java 25: shutdown() removed - automatic on scope close
                // scope.shutdown();
                logger.error("Maximum duration exceeded");
                for (int i = 0; i < subtasks.size(); i++) {
                    var subtask = subtasks.get(i);
                    if (subtask.state() == StructuredTaskScope.Subtask.State.UNAVAILABLE) {
                        cancelledTasks.add(i);
                    }
                }
                scope.join();
                
                return new CancellationResult<>(
                    allResults, 
                    cancelledTasks, 
                    "Maximum duration exceeded", 
                    System.currentTimeMillis() - startTime
                );
            }

            return new CancellationResult<>(
                allResults, 
                cancelledTasks, 
                null, 
                System.currentTimeMillis() - startTime
            );
            
        }
    }

}

    private static class ProgressiveResults<T> {
        
        @FunctionalInterface
        public interface ProgressCallback<T> {
            void onProgress(int taskIndex, T result);
        }
        
        public ProgressiveSummary<T> executeWithProgressCallback(
            List<Callable<T>> tasks, 
            ProgressCallback<T> callback) throws InterruptedException {
        
        return executeWithProgressCallback(tasks, callback, Duration.ofSeconds(30));
    }
    
    public ProgressiveSummary<T> executeWithProgressCallback(
            List<Callable<T>> tasks, 
            ProgressCallback<T> callback,
            Duration maxDuration) throws InterruptedException {
        
        long startTime = System.currentTimeMillis();
        List<T> results = new ArrayList<>(Collections.nCopies(tasks.size(), null));
        List<Exception> errors = new ArrayList<>();
        boolean[] completed = new boolean[tasks.size()];
        ConcurrentHashMap<Integer, T> resultsByIndex = new ConcurrentHashMap<>();
        int totalCompleted = 0;
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();

            for (int i = 0; i < tasks.size(); i++) {
                int taskIndex = i;
                Callable<T> task = tasks.get(i);
                subtasks.add(scope.fork(() -> {
                    T result = task.call();
                    resultsByIndex.put(taskIndex, result);
                    return result;
                }));
            }

            Instant maxTime = Instant.now().plus(maxDuration);
            
            while (totalCompleted < tasks.size() && Instant.now().isBefore(maxTime)) {
                // Java 25: joinUntil removed - use sleep for polling
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                for (int i = 0; i < subtasks.size(); i++) {
                    if (!completed[i]) {
                        var subtask = subtasks.get(i);
                        
                        switch (subtask.state()) {
                            case SUCCESS:
                                T result = resultsByIndex.get(i);
                                results.set(i, result);
                                completed[i] = true;
                                totalCompleted++;

                                callback.onProgress(i, result);
                                break;
                                
                            case FAILED:
                                errors.add(new RuntimeException("Task " + i + " failed"));
                                completed[i] = true;
                                totalCompleted++;
                                break;
                                
                            case UNAVAILABLE:
                                // Still running; check again on the next polling pass.
                                break;
                        }
                    }
                }
            }

            if (Instant.now().isAfter(maxTime)) {
                System.out.printf("   Progressive execution timed out after %d ms%n", 
                    maxDuration.toMillis());
                // Java 25: shutdown() removed - automatic on scope close
                // scope.shutdown();
            }

            scope.join();
            
            return new ProgressiveSummary<>(
                results.stream().filter(Objects::nonNull).collect(toList()),
                errors,
                totalCompleted,
                tasks.size(),
                System.currentTimeMillis() - startTime,
                Instant.now().isAfter(maxTime)
            );
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected timeout in progressive results", e);
        }
    }
    
    public static class ProgressiveSummary<T> {
        private final List<T> completedResults;
        private final List<Exception> errors;
        private final int completedCount;
        private final int totalCount;
        private final long executionTimeMs;
        private final boolean timedOut;
        
        public ProgressiveSummary(List<T> completedResults, List<Exception> errors, 
                                int completedCount, int totalCount, long executionTimeMs, boolean timedOut) {
            this.completedResults = completedResults;
            this.errors = errors;
            this.completedCount = completedCount;
            this.totalCount = totalCount;
            this.executionTimeMs = executionTimeMs;
            this.timedOut = timedOut;
        }

        public List<T> getCompletedResults() { return completedResults; }
        public List<Exception> getErrors() { return errors; }
        public int getCompletedCount() { return completedCount; }
        public int getTotalCount() { return totalCount; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public boolean isTimedOut() { return timedOut; }
        public double getCompletionRate() { return (double) completedCount / totalCount; }
        
        @Override
        public String toString() {
            return String.format("ProgressiveSummary{completed=%d/%d (%.1f%%), errors=%d, time=%dms, timedOut=%s}",
                completedCount, totalCount, getCompletionRate() * 100, 
                errors.size(), executionTimeMs, timedOut);
        }
    }
}

    private static class HierarchicalTaskManager {
        
        public String executeHierarchical() throws Exception {
            try (var parentScope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                
                var childTask1 = parentScope.fork(() -> executeChildTasks("Group-1"));
                var childTask2 = parentScope.fork(() -> executeChildTasks("Group-2"));
                var childTask3 = parentScope.fork(() -> executeChildTasks("Group-3"));
                
                parentScope.join();
                
                return String.format("Parent completed: [%s, %s, %s]", 
                                   childTask1.get(), childTask2.get(), childTask3.get());
            }
        }
        
        private String executeChildTasks(String group) throws Exception {
            try (var childScope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                
                var task1 = childScope.fork(() -> {
                    Thread.sleep(50);
                    return group + "-Task-1";
                });
                
                var task2 = childScope.fork(() -> {
                    Thread.sleep(100);
                    return group + "-Task-2";
                });
                
                childScope.join();
                
                return String.format("%s: [%s, %s]", group, task1.get(), task2.get());
            }
        }
    }

    private enum ResourceType { CPU, MEMORY, IO }
    
    private static class ResourceTask {
        private final String name;
        private final ResourceType type;
        private final int duration;
        
        public ResourceTask(String name, ResourceType type, int duration) {
            this.name = name;
            this.type = type;
            this.duration = duration;
        }
        
        public String getName() { return name; }
        public ResourceType getType() { return type; }
        public int getDuration() { return duration; }
    }
    
    private static class ResourceAwareScheduler {
        
        public List<String> executeResourceAware(List<ResourceTask> tasks) throws Exception {
            var cpuTasks = tasks.stream().filter(t -> t.getType() == ResourceType.CPU).toList();
            var memoryTasks = tasks.stream().filter(t -> t.getType() == ResourceType.MEMORY).toList();
            var ioTasks = tasks.stream().filter(t -> t.getType() == ResourceType.IO).toList();
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                
                var cpuResult = scope.fork(() -> executeResourceGroup(cpuTasks));
                var memoryResult = scope.fork(() -> executeResourceGroup(memoryTasks));
                var ioResult = scope.fork(() -> executeResourceGroup(ioTasks));
                
                scope.join();
                
                List<String> allResults = new ArrayList<>();
                allResults.addAll(cpuResult.get());
                allResults.addAll(memoryResult.get());
                allResults.addAll(ioResult.get());
                
                return allResults;
            }
        }
        
        private List<String> executeResourceGroup(List<ResourceTask> tasks) throws Exception {
            List<String> results = new ArrayList<>();
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();
                
                for (ResourceTask task : tasks) {
                    subtasks.add(scope.fork(() -> {
                        Thread.sleep(task.getDuration() * 100);
                        return task.getName() + " completed";
                    }));
                }
                
                scope.join();
                
                for (var subtask : subtasks) {
                    results.add(subtask.get());
                }
            }
            
            return results;
        }
    }

    private static class AdaptiveConcurrency<T> {
        
        public List<T> executeAdaptive(List<Callable<T>> tasks) throws Exception {
            int batchSize = Math.min(5, tasks.size());
            List<T> allResults = new ArrayList<>();
            
            for (int i = 0; i < tasks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, tasks.size());
                List<Callable<T>> batch = tasks.subList(i, end);
                
                long startTime = System.currentTimeMillis();
                List<T> batchResults = executeBatch(batch);
                long duration = System.currentTimeMillis() - startTime;
                
                allResults.addAll(batchResults);

                if (duration < 100) {
                    batchSize = Math.min(batchSize * 2, 10);
                } else if (duration > 500) {
                    batchSize = Math.max(batchSize / 2, 2);
                }
                
                System.out.printf("  Batch completed in %dms, next batch size: %d%n", 
                                 duration, batchSize);
            }
            
            return allResults;
        }
        
        private List<T> executeBatch(List<Callable<T>> batch) throws Exception {
            List<T> results = new ArrayList<>();
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();
                
                for (Callable<T> task : batch) {
                    subtasks.add(scope.fork(task));
                }
                
                scope.join();
                
                for (var subtask : subtasks) {
                    results.add(subtask.get());
                }
            }
            
            return results;
        }
    }

    private static class BulkheadPattern {
        
        public static class BulkheadResult {
            private final List<String> criticalResults;
            private final List<String> normalResults;
            
            public BulkheadResult(List<String> criticalResults, List<String> normalResults) {
                this.criticalResults = criticalResults;
                this.normalResults = normalResults;
            }
            
            @Override
            public String toString() {
                return String.format("Critical: %s, Normal: %s", criticalResults, normalResults);
            }
        }
        
        public BulkheadResult executeWithBulkhead(List<Callable<String>> criticalTasks, 
                                                 List<Callable<String>> normalTasks) throws Exception {
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

                var criticalBulkhead = scope.fork(() -> executeBulkhead(criticalTasks, "Critical"));
                var normalBulkhead = scope.fork(() -> executeBulkhead(normalTasks, "Normal"));
                
                scope.join();
                
                return new BulkheadResult(criticalBulkhead.get(), normalBulkhead.get());
            }
        }
        
        private List<String> executeBulkhead(List<Callable<String>> tasks, String bulkheadName) throws Exception {
            List<String> results = new ArrayList<>();
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();
                
                for (Callable<String> task : tasks) {
                    subtasks.add(scope.fork(task));
                }
                
                scope.join();
                
                for (var subtask : subtasks) {
                    results.add(subtask.get());
                }
            }
            
            System.out.printf("  %s bulkhead completed with %d results%n", bulkheadName, results.size());
            return results;
        }
    }
}
