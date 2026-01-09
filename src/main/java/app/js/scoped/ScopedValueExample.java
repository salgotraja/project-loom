package app.js.scoped;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadLocalRandom;

public class ScopedValueExample {

    private static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    
    static void main(String[] args) throws Exception {
        System.out.println(" Scoped Values Demo");
        System.out.println("=====================");

        for (int i = 1; i <= 3; i++) {
            final int requestNum = i;
            
            Thread.startVirtualThread(() -> {
                try {
                    processRequest("user-" + requestNum, "req-" + requestNum);
                } catch (Exception e) {
                    System.err.println("Request " + requestNum + " failed: " + e.getMessage());
                }
            });
        }

        Thread.sleep(3000);
        
        System.out.println("\n Testing Scoped Values with Structured Concurrency");
        testStructuredConcurrencyWithScopedValues();
        
        System.out.println("\n Testing Scoped Values Inheritance");
        testScopedValueInheritance();
        
        System.out.println("\n Performance Comparison: ScopedValue vs ThreadLocal");
        performanceComparison();
    }
    
    public static void processRequest(String userId, String requestId) {
        ScopedValue.where(USER_ID, userId)
                   .where(REQUEST_ID, requestId)
                   .where(CORRELATION_ID, "corr-" + requestId)
                   .where(TENANT_ID, "tenant-" + userId.hashCode() % 3)
                   .run(() -> {
                       try {
                           handleBusinessLogic();
                       } catch (Exception e) {
                           throw new RuntimeException(e);
                       }
                   });
    }
    
    private static void handleBusinessLogic() throws Exception {
        logContext("Starting business logic");

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            
            var authTask = scope.fork(() -> {
                logContext("Authenticating user");
                Thread.sleep(100 + ThreadLocalRandom.current().nextInt(50));
                return "Auth successful for " + getCurrentUserId();
            });
            
            var dataTask = scope.fork(() -> {
                logContext("Fetching user data");
                Thread.sleep(150 + ThreadLocalRandom.current().nextInt(50));
                return "Data fetched for " + getCurrentUserId();
            });
            
            var auditTask = scope.fork(() -> {
                logContext("Creating audit log");
                Thread.sleep(80 + ThreadLocalRandom.current().nextInt(30));
                return "Audit logged for " + getCurrentUserId();
            });
            
            scope.join();
            scope.throwIfFailed();
            
            logContext("Business logic completed: " + 
                      authTask.get() + " | " + 
                      dataTask.get() + " | " + 
                      auditTask.get());
        }
    }
    
    private static void testStructuredConcurrencyWithScopedValues() throws Exception {
        ScopedValue.where(USER_ID, "test-user")
                   .where(REQUEST_ID, "test-req")
                   .where(CORRELATION_ID, "test-corr")
                   .run(() -> {
                       try {
                           performParallelOperations();
                       } catch (Exception e) {
                           throw new RuntimeException(e);
                       }
                   });
    }
    
    private static void performParallelOperations() throws Exception {
        logContext("Starting parallel operations");
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var tasks = new StructuredTaskScope.Subtask[5];
            
            for (int i = 0; i < 5; i++) {
                final int taskNum = i;
                tasks[i] = scope.fork(() -> {
                    logContext("Executing task " + taskNum);
                    Thread.sleep(100);
                    return "Task " + taskNum + " completed for " + getCurrentUserId();
                });
            }
            
            scope.join();
            scope.throwIfFailed();
            
            for (var task : tasks) {
                System.out.println("   " + task.get());
            }
        }
    }
    
    private static void testScopedValueInheritance() throws Exception {
        ScopedValue.where(USER_ID, "parent-user")
                   .where(REQUEST_ID, "parent-req")
                   .run(() -> {
                       try {
                           logContext("Parent context");

                           Thread childThread = Thread.startVirtualThread(() -> {
                               logContext("Child context - inherited values");

                               Thread.startVirtualThread(() -> {
                                   logContext("Grandchild context - inherited values");
                               });
                           });
                           
                           childThread.join();
                           Thread.sleep(100);
                           
                       } catch (Exception e) {
                           throw new RuntimeException(e);
                       }
                   });
    }
    
    private static void performanceComparison() {
        final int ITERATIONS = 100_000;

        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            threadLocal.set("value-" + i);
            threadLocal.get();
        }
        threadLocal.remove();
        long threadLocalTime = System.nanoTime() - startTime;

        ScopedValue<String> scopedValue = ScopedValue.newInstance();
        
        startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            final int iteration = i;
            ScopedValue.where(scopedValue, "value-" + iteration)
                       .run(() -> {
                           scopedValue.get();
                       });
        }
        long scopedValueTime = System.nanoTime() - startTime;
        
        System.out.printf("ThreadLocal time: %.2f ms%n", threadLocalTime / 1_000_000.0);
        System.out.printf("ScopedValue time: %.2f ms%n", scopedValueTime / 1_000_000.0);
        System.out.printf("ScopedValue is %.2fx %s%n", 
                         Math.abs(threadLocalTime - scopedValueTime) / (double) Math.min(threadLocalTime, scopedValueTime),
                         scopedValueTime < threadLocalTime ? "faster" : "slower");
    }

    private static String getCurrentUserId() {
        return USER_ID.isBound() ? USER_ID.get() : "unknown";
    }
    
    private static String getCurrentRequestId() {
        return REQUEST_ID.isBound() ? REQUEST_ID.get() : "unknown";
    }
    
    private static String getCurrentCorrelationId() {
        return CORRELATION_ID.isBound() ? CORRELATION_ID.get() : "unknown";
    }
    
    private static String getCurrentTenantId() {
        return TENANT_ID.isBound() ? TENANT_ID.get() : "unknown";
    }
    
    private static void logContext(String message) {
        System.out.printf("[%s] [%s] [%s] [%s] %s (Thread: %s)%n",
                         getCurrentUserId(),
                         getCurrentRequestId(),
                         getCurrentCorrelationId(),
                         getCurrentTenantId(),
                         message,
                         Thread.currentThread().getName());
    }
}