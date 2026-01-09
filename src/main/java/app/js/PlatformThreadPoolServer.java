package app.js;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlatformThreadPoolServer {
    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 20;
    private static final long BLOCKING_SIMULATION_TIME = 200;

    public static void main(String[] args) throws IOException {
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        System.out.println("Platform Thread Pool Server started on port " + PORT);
        System.out.println("Thread pool size: " + THREAD_POOL_SIZE);
        System.out.println("Simulated Blocking Time: " + BLOCKING_SIMULATION_TIME + " ms");
        System.out.println("Access: http://localhost:" + PORT + "/api");

        server.createContext("/api", exchange -> {
            threadPoolExecutor.submit(() -> {
                try {
                    String currentThreadName = Thread.currentThread().getName();
                    System.out.println("Received request on platform thread pool: " + currentThreadName);
                    Thread.sleep(BLOCKING_SIMULATION_TIME);
                    String response = "Platform Thread Ok\n";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });

        server.setExecutor(null);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down platform thread pool...");
            threadPoolExecutor.shutdown();
            try {
                if (!threadPoolExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPoolExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPoolExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Platform Thread Pool Server stopped.");
        }));
    }
}
