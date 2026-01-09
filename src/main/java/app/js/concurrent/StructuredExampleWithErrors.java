package app.js.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

public class StructuredExampleWithErrors {
    private static final Logger logger = LoggerFactory.getLogger(StructuredExampleWithErrors.class);
    static void main(String[] args) {
        logger.info("=== Structured Concurrency with Error Handling ===");
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<String> fetch1 = scope.fork(() -> fetchFromService("Service-1", 300, false));
            Subtask<String> fetch2 = scope.fork(() -> fetchFromService("Service-2", 200, true));
            Subtask<String> fetch3 = scope.fork(() -> fetchFromService("Service-3", 100, false));

            scope.join();
            scope.throwIfFailed();

            String result = fetch1.get() + fetch2.get() + fetch3.get();
            logger.info("Combined result: {}", result);
            
        } catch (Exception e) {
            logger.error("One or more services failed: {}", e.getMessage());
            logger.error("All remaining tasks were cancelled automatically");
        }
    }
    
    static String fetchFromService(String serviceName, long delay, boolean shouldFail) throws Exception {
        Thread.sleep(delay);
        
        if (shouldFail) {
            throw new RuntimeException(serviceName + " failed!");
        }

        logger.info("{} completed successfully on thread: {}", serviceName, Thread.currentThread().getName());
        return serviceName + " result ";
    }
}