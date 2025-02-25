package org.davidgeorgehope.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NginxFrontendLogClient {
    private static final Logger logger = LoggerFactory.getLogger(NginxFrontendLogClient.class);
    private static String hostName = "localhost"; // Default to localhost if not specified
    private static final String LOG_DIR = System.getenv().getOrDefault("LOG_DIRECTORY", "/var/log/nginx_frontend");
    private static final String ACCESS_LOG = "access.log";
    private static final String ERROR_LOG = "error.log";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java org.davidgeorgehope.client.NginxFrontendLogClient <error-port> <stdout-port> [hostname]");
            System.exit(1);
        }

        int errorPort = Integer.parseInt(args[0]);
        int stdoutPort = Integer.parseInt(args[1]);
        
        // Use the hostname if provided
        if (args.length >= 3 && args[2] != null && !args[2].trim().isEmpty()) {
            hostName = args[2].trim();
            logger.info("Using host: " + hostName);
        }

        // Create log directory if it doesn't exist
        try {
            Path logDirPath = Paths.get(LOG_DIR);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
                logger.info("Created log directory: " + LOG_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create log directory: " + LOG_DIR, e);
            System.exit(1);
        }

        // Start threads to read from each port
        Thread errorThread = new Thread(() -> readFromPort(errorPort, true));
        Thread accessThread = new Thread(() -> readFromPort(stdoutPort, false));

        errorThread.setDaemon(true);
        accessThread.setDaemon(true);

        errorThread.start();
        accessThread.start();

        try {
            // Wait for both threads indefinitely
            errorThread.join();
            accessThread.join();
        } catch (InterruptedException e) {
            logger.error("Client interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static void readFromPort(int port, boolean isError) {
        try {
            logger.info("Connecting to " + hostName + ":" + port + " for " + (isError ? "error" : "access") + " logs");
            String logFile = isError ? ERROR_LOG : ACCESS_LOG;
            Path logPath = Paths.get(LOG_DIR, logFile);
            
            logger.info("Writing logs to: " + logPath);
            
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket(hostName, port);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(new FileWriter(logPath.toString(), true))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Format with timestamp for Nginx logs if it's an error log
                        String logEntry;
                        if (isError) {
                            logEntry = line;
                        } else {
                            // For access logs, format like a normal Nginx access log
                            logEntry = line;
                        }
                        
                        // Write to file
                        writer.println(logEntry);
                        writer.flush();
                        
                        // Also output to console for debugging
                        if (isError) {
                            System.err.println(logEntry);
                        } else {
                            System.out.println(logEntry);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading from " + hostName + ":" + port + ", retrying in 5 seconds", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fatal error in client for " + hostName + ":" + port, e);
        }
    }
} 