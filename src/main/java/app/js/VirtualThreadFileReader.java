package app.js;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VirtualThreadFileReader {
    private static final String FILENAME = "large.txt";
    private static final int NUMBER_OF_LINES = 10000000;

    public static void main(String[] args) throws IOException, InterruptedException {
        Path filePath = Paths.get(FILENAME);
        if (!Files.exists(filePath)) {
            System.out.println("Creating dummy file: " + FILENAME + " with " + NUMBER_OF_LINES + " lines");
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < NUMBER_OF_LINES; i++) {
                    sb.append("This is the line number ").append(i + 1).append(" in the large file.\n");
                }
                Files.writeString(filePath, sb.toString());
                System.out.println("Dummy file created successfully.");
            } catch (IOException e) {
                System.err.println("Failed to create dummy file: " + e.getMessage());
                return;
            }
        } else {
            System.out.println("Dummy file '" + FILENAME + "' already exists. Using existing file.");
        }

        System.out.println("\n---- Starting file read operation ----");

        Thread virtualFileReaderThread = Thread.startVirtualThread(() -> {
            try {
                long startTime = System.nanoTime();
                System.out.println("Virtual thread '" + Thread.currentThread().getName() + "' started reading file.");

                List<String> lines = Files.readAllLines(filePath);

                long endTime = System.nanoTime();
                long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                System.out.println("Virtual thread '" + Thread.currentThread().getName() + "' finished reading file.");
                System.out.println("Total lines read: " + lines.size());
                System.out.println("Time taken by virtual thread: " + durationMs + " ms");

            } catch (IOException e) {
                System.err.println("Failed to read file: " + e.getMessage());
            }
        });

        System.out.println("Main thread continues its work while virtual thread reads the file...");
        Thread.sleep(50);

        System.out.println("Main thread waiting for virtual thread to complete...");
        virtualFileReaderThread.join();

        System.out.println("\n--- File read operation completed. Main thread exiting. ---");

         Files.deleteIfExists(filePath);
    }
}
