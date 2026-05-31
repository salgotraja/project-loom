package app.js.structured;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

public class HierarchicalProgressiveHandler {
    private static final Logger logger = LoggerFactory.getLogger(HierarchicalProgressiveHandler.class);

    private final TaskMetrics metrics = new TaskMetrics();
    private final ProgressTracker progressTracker = new ProgressTracker();

    public <T> HierarchicalResult<T> executeHierarchical(
            HierarchicalRequest<T> request) throws Exception {

        long startTime = System.currentTimeMillis();
        String executionId = generateExecutionId();

        logger.info("Starting hierarchical execution: {}", executionId);
        progressTracker.startExecution(executionId, request.getTotalTaskCount());

        try {
            T result = executeWithHierarchy(request, executionId);

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(duration);

            return new HierarchicalResult<>(
                    result,
                    duration,
                    progressTracker.getFinalProgress(executionId),
                    true,
                    null
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordFailure(duration, e);

            return new HierarchicalResult<>(
                    null,
                    duration,
                    progressTracker.getFinalProgress(executionId),
                    false,
                    e.getMessage()
            );
        }
    }

    public <T> ProgressiveResult<T> executeProgressive(
            ProgressiveRequest<T> request) throws Exception {

        long startTime = System.currentTimeMillis();
        String executionId = generateExecutionId();

        progressTracker.startExecution(executionId, request.getTasks().size());
        logger.info("Starting progressive execution: {} with {} tasks", executionId, request.getTasks().size());

        Map<Integer, T> resultsByIndex = new ConcurrentHashMap<>();
        Map<Integer, Exception> errorsByIndex = new ConcurrentHashMap<>();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<T>awaitAll())) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();

            for (int i = 0; i < request.getTasks().size(); i++) {
                final int taskIndex = i;
                Callable<T> task = request.getTasks().get(i);

                subtasks.add(scope.fork(() -> {
                    try {
                        logger.debug("Starting task {}", taskIndex);
                        T result = task.call();
                        resultsByIndex.put(taskIndex, result);
                        progressTracker.updateProgress(executionId, taskIndex, "completed");
                        request.getProgressCallback().accept(new ProgressUpdate<>(taskIndex, result, null));
                        logger.debug("Task {} completed successfully", taskIndex);
                        return result;
                    } catch (Exception e) {
                        logger.debug("Task {} failed: {}", taskIndex, e.getMessage());
                        errorsByIndex.put(taskIndex, e);
                        progressTracker.updateProgress(executionId, taskIndex, "failed: " + e.getMessage());
                        request.getProgressCallback().accept(new ProgressUpdate<>(taskIndex, null, e));
                        throw e;
                    }
                }));
            }

            Duration timeout = request.getTimeout();
            Instant deadline = Instant.now().plus(timeout);

            boolean[] completed = new boolean[subtasks.size()];
            int totalCompleted = 0;

            while (totalCompleted < subtasks.size() && Instant.now().isBefore(deadline)) {
                // Java 25: joinUntil removed - use sleep for polling
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                for (int i = 0; i < subtasks.size(); i++) {
                    if (!completed[i]) {
                        var subtask = subtasks.get(i);
                        var state = subtask.state();

                        switch (state) {
                            case SUCCESS:
                                completed[i] = true;
                                totalCompleted++;
                                logger.debug("Observed completed task {}", i);
                                break;

                            case FAILED:
                                logger.debug("Task {} failed", i);
                                errorsByIndex.putIfAbsent(i, new RuntimeException("Task " + i + " failed"));
                                completed[i] = true;
                                totalCompleted++;
                                break;

                            case UNAVAILABLE:

                                break;
                        }
                    }
                }
            }

            boolean timedOut = Instant.now().isAfter(deadline);
            if (timedOut) {
                logger.warn("Progressive execution timed out: {} after {}ms", executionId, timeout.toMillis());
            }

            scope.join();

            for (int i = 0; i < subtasks.size(); i++) {
                if (!completed[i]) {
                    var subtask = subtasks.get(i);
                    if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
                        errorsByIndex.putIfAbsent(i, new RuntimeException("Task " + i + " failed"));
                    }
                    completed[i] = true;
                    totalCompleted++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            boolean allCompleted = totalCompleted == subtasks.size();
            List<T> results = IntStream.range(0, subtasks.size())
                    .filter(resultsByIndex::containsKey)
                    .mapToObj(resultsByIndex::get)
                    .toList();
            List<Exception> errors = IntStream.range(0, subtasks.size())
                    .filter(errorsByIndex::containsKey)
                    .mapToObj(errorsByIndex::get)
                    .toList();

            logger.info("Progressive execution {} completed: {}/{} tasks in {}ms (timedOut={})", 
                       executionId, results.size(), subtasks.size(), duration, timedOut);

            metrics.recordSuccess(duration);

            progressTracker.cleanupExecution(executionId);

            return new ProgressiveResult<>(
                    results,
                    errors,
                    duration,
                    allCompleted,
                    executionId
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Progressive execution {} failed after {}ms", executionId, duration, e);

            metrics.recordFailure(duration, e);

            progressTracker.cleanupExecution(executionId);
            
            throw new RuntimeException("Progressive execution failed", e);
        }
    }

    public <T> CombinedResult<T> executeCombined(
            CombinedRequest<T> request) throws Exception {

        long startTime = System.currentTimeMillis();
        String executionId = generateExecutionId();

        Map<String, Object> levelResults = new ConcurrentHashMap<>();
        List<String> completedLevels = new ArrayList<>();

        try (var parentScope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

            List<StructuredTaskScope.Subtask<Map<String, Object>>> levelTasks = new ArrayList<>();

            for (var level : request.getLevels()) {
                levelTasks.add(parentScope.fork(() -> {
                    String levelId = level.getName();
                    logger.info("Starting level: {} in execution: {}", levelId, executionId);

                    ProgressiveRequest<T> progressiveRequest = new ProgressiveRequest<>(
                            level.getTasks(),
                            level.getTimeout(),
                            (update) -> {
                                request.getProgressCallback().accept(
                                        new CombinedProgressUpdate<>(levelId, update)
                                );
                            }
                    );

                    ProgressiveResult<T> levelResult = executeProgressive(progressiveRequest);

                    Map<String, Object> result = Map.of(
                            "levelId", levelId,
                            "results", levelResult.getResults(),
                            "duration", levelResult.getDurationMs(),
                            "completed", levelResult.isCompleted()
                    );

                    logger.info("Completed level: {} in {}ms", levelId, levelResult.getDurationMs());
                    return result;
                }));
            }

            parentScope.join();

            for (var levelTask : levelTasks) {
                Map<String, Object> levelResult = levelTask.get();
                String levelId = (String) levelResult.get("levelId");
                levelResults.put(levelId, levelResult);
                completedLevels.add(levelId);
            }

            long duration = System.currentTimeMillis() - startTime;

            return new CombinedResult<>(
                    levelResults,
                    completedLevels,
                    duration,
                    true,
                    executionId
            );

        }
    }

    private <T> T executeWithHierarchy(HierarchicalRequest<T> request, String executionId) throws Exception {

        return request.getRootTask().call();
    }

    private String generateExecutionId() {
        return "exec-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }

    public TaskMetrics getMetrics() {
        return metrics;
    }

    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    public static class HierarchicalRequest<T> {
        private final Callable<T> rootTask;
        private final int totalTaskCount;
        private final Duration timeout;

        public HierarchicalRequest(Callable<T> rootTask, int totalTaskCount, Duration timeout) {
            this.rootTask = rootTask;
            this.totalTaskCount = totalTaskCount;
            this.timeout = timeout;
        }

        public Callable<T> getRootTask() { return rootTask; }
        public int getTotalTaskCount() { return totalTaskCount; }
        public Duration getTimeout() { return timeout; }
    }

    public static class ProgressiveRequest<T> {
        private final List<Callable<T>> tasks;
        private final Duration timeout;
        private final Consumer<ProgressUpdate<T>> progressCallback;

        public ProgressiveRequest(List<Callable<T>> tasks, Duration timeout,
                                  Consumer<ProgressUpdate<T>> progressCallback) {
            this.tasks = tasks;
            this.timeout = timeout;
            this.progressCallback = progressCallback;
        }

        public List<Callable<T>> getTasks() { return tasks; }
        public Duration getTimeout() { return timeout; }
        public Consumer<ProgressUpdate<T>> getProgressCallback() { return progressCallback; }
    }

    public static class CombinedRequest<T> {
        private final List<Level<T>> levels;
        private final Consumer<CombinedProgressUpdate<T>> progressCallback;

        public CombinedRequest(List<Level<T>> levels, Consumer<CombinedProgressUpdate<T>> progressCallback) {
            this.levels = levels;
            this.progressCallback = progressCallback;
        }

        public List<Level<T>> getLevels() { return levels; }
        public Consumer<CombinedProgressUpdate<T>> getProgressCallback() { return progressCallback; }

        public static class Level<T> {
            private final String name;
            private final List<Callable<T>> tasks;
            private final Duration timeout;

            public Level(String name, List<Callable<T>> tasks, Duration timeout) {
                this.name = name;
                this.tasks = tasks;
                this.timeout = timeout;
            }

            public String getName() { return name; }
            public List<Callable<T>> getTasks() { return tasks; }
            public Duration getTimeout() { return timeout; }
        }
    }

    public static class HierarchicalResult<T> {
        private final T result;
        private final long durationMs;
        private final Map<String, String> progress;
        private final boolean successful;
        private final String error;

        public HierarchicalResult(T result, long durationMs, Map<String, String> progress,
                                  boolean successful, String error) {
            this.result = result;
            this.durationMs = durationMs;
            this.progress = progress;
            this.successful = successful;
            this.error = error;
        }

        public T getResult() { return result; }
        public long getDurationMs() { return durationMs; }
        public Map<String, String> getProgress() { return progress; }
        public boolean isSuccessful() { return successful; }
        public String getError() { return error; }
    }

    public static class ProgressiveResult<T> {
        private final List<T> results;
        private final List<Exception> errors;
        private final long durationMs;
        private final boolean completed;
        private final String executionId;

        public ProgressiveResult(List<T> results, List<Exception> errors, long durationMs,
                                 boolean completed, String executionId) {
            this.results = results;
            this.errors = errors;
            this.durationMs = durationMs;
            this.completed = completed;
            this.executionId = executionId;
        }

        public List<T> getResults() { return results; }
        public List<Exception> getErrors() { return errors; }
        public long getDurationMs() { return durationMs; }
        public boolean isCompleted() { return completed; }
        public String getExecutionId() { return executionId; }
    }

    public static class CombinedResult<T> {
        private final Map<String, Object> levelResults;
        private final List<String> completedLevels;
        private final long durationMs;
        private final boolean successful;
        private final String executionId;

        public CombinedResult(Map<String, Object> levelResults, List<String> completedLevels,
                              long durationMs, boolean successful, String executionId) {
            this.levelResults = levelResults;
            this.completedLevels = completedLevels;
            this.durationMs = durationMs;
            this.successful = successful;
            this.executionId = executionId;
        }

        public Map<String, Object> getLevelResults() { return levelResults; }
        public List<String> getCompletedLevels() { return completedLevels; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccessful() { return successful; }
        public String getExecutionId() { return executionId; }
    }

    public static class ProgressUpdate<T> {
        private final int taskIndex;
        private final T result;
        private final Exception error;

        public ProgressUpdate(int taskIndex, T result, Exception error) {
            this.taskIndex = taskIndex;
            this.result = result;
            this.error = error;
        }

        public int getTaskIndex() { return taskIndex; }
        public T getResult() { return result; }
        public Exception getError() { return error; }
        public boolean isSuccess() { return error == null; }
    }

    public static class CombinedProgressUpdate<T> {
        private final String levelId;
        private final ProgressUpdate<T> progressUpdate;

        public CombinedProgressUpdate(String levelId, ProgressUpdate<T> progressUpdate) {
            this.levelId = levelId;
            this.progressUpdate = progressUpdate;
        }

        public String getLevelId() { return levelId; }
        public ProgressUpdate<T> getProgressUpdate() { return progressUpdate; }
    }

    public static class TaskMetrics {
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong successfulExecutions = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

        public void recordSuccess(long durationMs) {
            totalExecutions.incrementAndGet();
            successfulExecutions.incrementAndGet();
            totalDuration.addAndGet(durationMs);
        }

        public void recordFailure(long durationMs, Exception error) {
            totalExecutions.incrementAndGet();
            totalDuration.addAndGet(durationMs);

            String errorType = error.getClass().getSimpleName();
            errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        }

        public double getSuccessRate() {
            long total = totalExecutions.get();
            return total > 0 ? (double) successfulExecutions.get() / total * 100 : 0;
        }

        public double getAverageDuration() {
            long total = totalExecutions.get();
            return total > 0 ? (double) totalDuration.get() / total : 0;
        }

        public Map<String, Long> getErrorCounts() {
            return errorCounts.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().get()
                    ));
        }

        public void printReport() {
            System.out.println("\n TASK METRICS REPORT");
            System.out.println("=====================");
            System.out.printf("Total Executions: %d%n", totalExecutions.get());
            System.out.printf("Successful: %d (%.1f%%)%n", successfulExecutions.get(), getSuccessRate());
            System.out.printf("Average Duration: %.1f ms%n", getAverageDuration());
            System.out.println("\nError Breakdown:");
            getErrorCounts().forEach((type, count) ->
                    System.out.printf("  %s: %d%n", type, count));
        }
    }

    public static class ProgressTracker {
        private final Map<String, ExecutionProgress> executions = new ConcurrentHashMap<>();

        public void startExecution(String executionId, int totalTasks) {
            executions.put(executionId, new ExecutionProgress(totalTasks));
        }

        public void updateProgress(String executionId, int taskIndex, String status) {
            ExecutionProgress progress = executions.get(executionId);
            if (progress != null) {
                progress.updateTask(taskIndex, status);
            }
        }

        public Map<String, String> getFinalProgress(String executionId) {
            ExecutionProgress progress = executions.get(executionId);
            return progress != null ? progress.getProgressSnapshot() : Map.of();
        }

        public void cleanupExecution(String executionId) {
            executions.remove(executionId);
        }

        private static class ExecutionProgress {
            private final int totalTasks;
            private final Map<Integer, String> taskStatuses = new ConcurrentHashMap<>();
            private final AtomicInteger completedTasks = new AtomicInteger(0);

            ExecutionProgress(int totalTasks) {
                this.totalTasks = totalTasks;
            }

            void updateTask(int taskIndex, String status) {
                String previousStatus = taskStatuses.put(taskIndex, status);
                if (previousStatus == null) {
                    completedTasks.incrementAndGet();
                }
            }

            Map<String, String> getProgressSnapshot() {
                return Map.of(
                        "totalTasks", String.valueOf(totalTasks),
                        "completedTasks", String.valueOf(completedTasks.get()),
                        "progressPercentage", String.valueOf(
                                totalTasks > 0 ? (completedTasks.get() * 100 / totalTasks) : 0
                        )
                );
            }
        }
    }
}
