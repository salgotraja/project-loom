package app.js.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

public class StructuredExample {
    private static final Logger logger = LoggerFactory.getLogger(StructuredExample.class);
    static void main(String[] args) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<String> fetch1 = scope.fork(() -> fetchFromService1());
            Subtask<String> fetch2 = scope.fork(() -> fetchFromService2());
            Subtask<String> fetch3 = scope.fork(() -> fetchFromService3());

            scope.join();
            scope.throwIfFailed();

            String result = fetch1.get() + fetch2.get() + fetch3.get();
            logger.info("Combined result: {}", result);
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
}