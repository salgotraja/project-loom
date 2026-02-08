package app.js.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.stream.Stream;

public class StructuredExampleWithSuccess {
    private static final Logger logger = LoggerFactory.getLogger(StructuredExampleWithSuccess.class);
    static void main(String[] args) throws Exception {
        logger.info("=== ShutdownOnFailure Example ===");
        runWithShutdownOnFailure();
        
        logger.info("\n=== ShutdownOnSuccess Example ===");
        runWithShutdownOnSuccess();
    }
    
    static void runWithShutdownOnFailure() throws Exception {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            Subtask<String> fetch1 = scope.fork(() -> fetchFromService1());
            Subtask<String> fetch2 = scope.fork(() -> fetchFromService2());
            Subtask<String> fetch3 = scope.fork(() -> fetchFromService3());

            scope.join();

            String result = fetch1.get() + fetch2.get() + fetch3.get();
            logger.info("All services completed: {}", result);
        }
    }
    
    static void runWithShutdownOnSuccess() throws Exception {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>allUntil(s -> s.state() == Subtask.State.SUCCESS)
        )) {
            scope.fork(() -> slowService("Service-A", 1000));
            scope.fork(() -> slowService("Service-B", 500));
            scope.fork(() -> slowService("Service-C", 200));

            Stream<Subtask<String>> results = scope.join();

            String result = results
                .filter(s -> s.state() == Subtask.State.SUCCESS)
                .findFirst()
                .map(Subtask::get)
                .orElseThrow(() -> new Exception("No successful result"));
            logger.info("First successful result: {}", result);
        }
    }
    
    static String fetchFromService1() throws Exception {
        Thread.sleep(300);
        logger.info("Service1 completed on thread: {}", Thread.currentThread().getName());
        return "Service1 ";
    }

    static String fetchFromService2() throws Exception {
        Thread.sleep(200);
        logger.info("Service2 completed on thread: {}", Thread.currentThread().getName());
        return "Service2 ";
    }

    static String fetchFromService3() throws Exception {
        Thread.sleep(100);
        logger.info("Service3 completed on thread: {}", Thread.currentThread().getName());
        return "Service3 ";
    }
    
    static String slowService(String name, long delay) throws Exception {
        Thread.sleep(delay);
        logger.info("{} completed on thread: {}", name, Thread.currentThread().getName());
        return name + " result";
    }
}