package app.js.reactive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.time.Duration;
import java.time.Instant;

public class VirtualThreadReactiveIntegration {
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadReactiveIntegration.class);
    static void main(String[] args) throws Exception {
        logger.info(" Virtual Thread + Reactive Streams Integration");
        logger.info("===============================================");

        logger.info("\n Test 1: Virtual Thread Publisher");
        testVirtualThreadPublisher();

        logger.info("\n Test 2: Reactive to Virtual Thread Bridge");
        testReactiveToVirtualBridge();

        logger.info("\n Test 3: Backpressure Handling");
        testBackpressureHandling();

        logger.info("\n Test 4: Error Handling Integration");
        testErrorHandlingIntegration();

        logger.info("\n Test 5: Performance Comparison");
        testPerformanceComparison();
        
        logger.info("\n All reactive integration tests completed!");
    }
    
    private static void testVirtualThreadPublisher() throws Exception {
        VirtualThreadPublisher<String> publisher = new VirtualThreadPublisher<>();

        publisher.subscribe(new SimpleSubscriber<>() {
            @Override
            public void onNext(String item) {
                System.out.printf("   Received: %s (Thread: %s)%n",
                        item, Thread.currentThread().getName());
            }
        });

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            for (int i = 0; i < 10; i++) {
                final int itemId = i;
                scope.fork(() -> {
                    Thread.sleep(100 + ThreadLocalRandom.current().nextInt(100));
                    publisher.publish("Item-" + itemId);
                    return null;
                });
            }
            
            scope.join();
        }
        
        Thread.sleep(500);
        publisher.complete();
    }
    
    private static void testReactiveToVirtualBridge() throws Exception {
        ReactiveToVirtualBridge<String> bridge = new ReactiveToVirtualBridge<>();

        List<String> reactiveData = List.of("Data-1", "Data-2", "Data-3", "Data-4", "Data-5");

        List<String> results = bridge.processReactiveStream(
            reactiveData.stream(),
            data -> {
                try {
                    Thread.sleep(50);
                    return data.toUpperCase() + "-PROCESSED";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        );
        
        System.out.printf("   Processed %d items: %s%n", results.size(), results);
    }
    
    private static void testBackpressureHandling() throws Exception {
        BackpressureHandler<Integer> handler = new BackpressureHandler<>(5);

        Thread.startVirtualThread(() -> {
            for (int i = 0; i < 20; i++) {
                try {
                    handler.offer(i);
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            handler.complete();
        });

        Thread.startVirtualThread(() -> {
            try {
                Integer item;
                while ((item = handler.take()) != null) {
                    System.out.printf("   Consumed: %d (Buffer size: %d)%n", 
                                     item, handler.getBufferSize());
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        Thread.sleep(3000);
    }
    
    private static void testErrorHandlingIntegration() throws Exception {
        ErrorHandlingIntegration<String> integration = new ErrorHandlingIntegration<>();
        
        List<String> input = List.of("success-1", "error", "success-2", "error", "success-3");
        
        var result = integration.processWithErrorHandling(
            input.stream(),
            item -> {
                if (item.equals("error")) {
                    throw new RuntimeException("Simulated error for: " + item);
                }
                return item.toUpperCase();
            }
        );
        
        System.out.printf("   Processed %d successful items, %d errors%n", 
                         result.getSuccessfulResults().size(), 
                         result.getErrors().size());
        System.out.printf("   Results: %s%n", result.getSuccessfulResults());
        System.out.printf("   Errors: %s%n", result.getErrors());
    }
    
    private static void testPerformanceComparison() throws Exception {
        final int ITEM_COUNT = 1000;
        final int PROCESSING_TIME = 10;

        long startTime = System.currentTimeMillis();
        List<String> traditionalResults = new ArrayList<>();
        
        for (int i = 0; i < ITEM_COUNT; i++) {
            Thread.sleep(PROCESSING_TIME);
            traditionalResults.add("Traditional-" + i);
        }
        
        long traditionalTime = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        List<String> virtualResults = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(ITEM_COUNT);
        
        for (int i = 0; i < ITEM_COUNT; i++) {
            final int itemId = i;
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(PROCESSING_TIME);
                    synchronized (virtualResults) {
                        virtualResults.add("Virtual-" + itemId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long virtualTime = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        List<String> structuredResults = new ArrayList<>();
        
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();
            
            for (int i = 0; i < ITEM_COUNT; i++) {
                final int itemId = i;
                subtasks.add(scope.fork(() -> {
                    Thread.sleep(PROCESSING_TIME);
                    return "Structured-" + itemId;
                }));
            }
            
            scope.join();
            
            for (var subtask : subtasks) {
                structuredResults.add(subtask.get());
            }
        }
        
        long structuredTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("   Traditional blocking: %dms%n", traditionalTime);
        System.out.printf("   Virtual threads: %dms (%.2fx faster)%n", 
                         virtualTime, (double) traditionalTime / virtualTime);
        System.out.printf("   Structured concurrency: %dms (%.2fx faster)%n", 
                         structuredTime, (double) traditionalTime / structuredTime);
        
        System.out.printf("   Results: Traditional=%d, Virtual=%d, Structured=%d%n",
                         traditionalResults.size(), virtualResults.size(), structuredResults.size());
    }

    private static class VirtualThreadPublisher<T> {
        private final Queue<SimpleSubscriber<T>> subscribers = new ConcurrentLinkedQueue<>();
        private final AtomicInteger itemCount = new AtomicInteger(0);
        
        public void subscribe(SimpleSubscriber<T> subscriber) {
            subscribers.offer(subscriber);
        }
        
        public void publish(T item) {
            Thread.startVirtualThread(() -> {
                for (SimpleSubscriber<T> subscriber : subscribers) {
                    try {
                        subscriber.onNext(item);
                    } catch (Exception e) {
                        subscriber.onError(e);
                    }
                }
            });
            itemCount.incrementAndGet();
        }
        
        public void complete() {
            Thread.startVirtualThread(() -> {
                for (SimpleSubscriber<T> subscriber : subscribers) {
                    subscriber.onComplete();
                }
            });
        }
        
        public int getItemCount() { return itemCount.get(); }
    }

    private static abstract class SimpleSubscriber<T> {
        public abstract void onNext(T item);
        public void onError(Throwable error) { 
            System.err.println("Error: " + error.getMessage());
        }
        public void onComplete() { 
            logger.info("   Subscription completed");
        }
    }

    private static class ReactiveToVirtualBridge<T> {
        
        public <R> List<R> processReactiveStream(Stream<T> stream, Function<T, R> processor) throws Exception {
            List<R> results = new ArrayList<>();
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<R>> subtasks = new ArrayList<>();
                
                stream.forEach(item -> {
                    subtasks.add(scope.fork(() -> processor.apply(item)));
                });
                
                scope.join();
                
                for (var subtask : subtasks) {
                    results.add(subtask.get());
                }
            }
            
            return results;
        }
    }

    private static class BackpressureHandler<T> {
        private final BlockingQueue<T> buffer;
        private final AtomicInteger bufferSize;
        private volatile boolean completed = false;
        
        public BackpressureHandler(int capacity) {
            this.buffer = new ArrayBlockingQueue<>(capacity);
            this.bufferSize = new AtomicInteger(0);
        }
        
        public void offer(T item) throws InterruptedException {
            buffer.put(item);
            bufferSize.incrementAndGet();
        }
        
        public T take() throws InterruptedException {
            if (completed && buffer.isEmpty()) {
                return null;
            }
            
            T item = buffer.take();
            bufferSize.decrementAndGet();
            return item;
        }
        
        public void complete() {
            completed = true;
        }
        
        public int getBufferSize() { return bufferSize.get(); }
    }

    private static class ErrorHandlingIntegration<T> {
        
        public static class ProcessingResult<T> {
            private final List<T> successfulResults;
            private final List<String> errors;
            
            public ProcessingResult(List<T> successfulResults, List<String> errors) {
                this.successfulResults = successfulResults;
                this.errors = errors;
            }
            
            public List<T> getSuccessfulResults() { return successfulResults; }
            public List<String> getErrors() { return errors; }
        }
        
        public <R> ProcessingResult<R> processWithErrorHandling(
                Stream<T> stream, 
                Function<T, R> processor) throws Exception {
            
            List<R> successfulResults = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<ProcessingResult<R>>> subtasks = new ArrayList<>();
                
                stream.forEach(item -> {
                    subtasks.add(scope.fork(() -> {
                        try {
                            R result = processor.apply(item);
                            return new ProcessingResult<>(List.of(result), List.of());
                        } catch (Exception e) {
                            return new ProcessingResult<>(List.<R>of(), List.of(e.getMessage()));
                        }
                    }));
                });
                
                scope.join();
                
                for (var subtask : subtasks) {
                    ProcessingResult<R> result = subtask.get();
                    successfulResults.addAll(result.getSuccessfulResults());
                    errors.addAll(result.getErrors());
                }
            }
            
            return new ProcessingResult<>(successfulResults, errors);
        }
    }
}