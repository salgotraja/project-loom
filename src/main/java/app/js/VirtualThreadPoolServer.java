package app.js;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VirtualThreadPoolServer {
    private static final int PORT = 8080;
    private static final long BLOCKING_SIMULATION_TIME = 200;

    public static void main(String[] args)  throws IOException {
        ExecutorService loomExecutor = Executors.newVirtualThreadPerTaskExecutor();
        System.out.println("Virtual Thread Pool Server started on port " + PORT);
        System.out.println("Simulated Blocking Time: " + BLOCKING_SIMULATION_TIME + " ms");
        System.out.println("Access: http://localhost:" + PORT + "/api");

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api", exchange -> {
            loomExecutor.submit(() -> {
                try {
                    String currentThreadName = Thread.currentThread().getName();
                    System.out.println("Received request on virtual thread pool: " + currentThreadName);
                    Thread.sleep(BLOCKING_SIMULATION_TIME);
                    String response = "Virtual Thread Ok\n";
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
            System.out.println("\nShutting down virtual thread pool...");
            loomExecutor.shutdown();
            try {
                if (!loomExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    loomExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                loomExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Virtual Thread Pool Server stopped.");
        }));
    }
}
