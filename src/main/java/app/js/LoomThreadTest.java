package app.js;

import java.util.ArrayList;
import java.util.List;

public class LoomThreadTest {

    public static void main(String[] args) throws InterruptedException {
        int n = 10_000_00;

        System.out.println("Running platform threads...");
        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        long end = System.currentTimeMillis();
        System.out.println("Platform threads took: " + (end - start) + " ms");

        System.out.println("\nRunning virtual threads...");
        start = System.currentTimeMillis();
        threads.clear();
        for (int i = 0; i < n; i++) {
            Thread t = Thread.ofVirtual().unstarted(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        end = System.currentTimeMillis();
        System.out.println("Virtual threads took: " + (end - start) + " ms");
    }
}
