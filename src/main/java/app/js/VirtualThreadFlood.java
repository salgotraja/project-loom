package app.js;

import java.util.ArrayList;
import java.util.List;

public class VirtualThreadFlood {
    private static final int PLATFORM_THREAD_COUNT = 1_00_00;
    private static final int VIRTUAL_THREAD_COUNT = 1_000_000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Thread Performance Comparison");
        System.out.println("Number of platform threads: " + PLATFORM_THREAD_COUNT);
        System.out.println("Number of virtual threads: " + VIRTUAL_THREAD_COUNT);
        System.out.println("Sleep duration: 1000ms each\n");

        testPlatformThreads();

        Thread.sleep(2000);

        testVirtualThreads();
    }

    private static void testPlatformThreads() throws InterruptedException {
        System.out.println("Testing Platform Threads...");
        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();

        try {
            for (int i = 0; i < PLATFORM_THREAD_COUNT; i++) {
                Thread t = new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                });
                threads.add(t);
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            long end = System.currentTimeMillis();
            System.out.println("Platform threads completed in: " + (end - start) + " ms");
            System.out.println("Platform threads created: " + threads.size());

        } catch (OutOfMemoryError e) {
            System.err.println("Platform threads failed due to memory constraints: " + e.getMessage());
            System.err.println("Successfully created " + threads.size() + " platform threads before failure");

            for (Thread t : threads) {
                if (t.isAlive()) {
                    t.interrupt();
                }
            }
        }
        System.out.println();
    }

    private static void testVirtualThreads() throws InterruptedException {
        System.out.println("Testing Virtual Threads...");
        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < VIRTUAL_THREAD_COUNT; i++) {
            Thread t = Thread.ofVirtual().unstarted(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            });
            t.start();
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join();
        }

        long end = System.currentTimeMillis();
        System.out.println("Virtual threads completed in: " + (end - start) + " ms");
        System.out.println("Virtual threads created: " + threads.size());
    }
}