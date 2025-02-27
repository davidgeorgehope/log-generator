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

public class MySQLLogClient {
    private static final Logger logger = LoggerFactory.getLogger(MySQLLogClient.class);
    private static String hostName = "localhost"; // Default to localhost if not specified
    private static final String LOG_DIR = System.getenv().getOrDefault("LOG_DIRECTORY", "/var/log/mysql");
    private static final String ERROR_LOG = "error.log";
    private static final String SLOW_LOG = "mysql-slow.log";
    private static final int LOG_RETENTION_DAYS = 1; // Keep logs for 1 day

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java org.davidgeorgehope.client.MySQLLogClient <error-port> <stdout-port> [hostname]");
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
        Thread stdoutThread = new Thread(() -> readFromPort(stdoutPort, false));

        errorThread.setDaemon(true);
        stdoutThread.setDaemon(true);

        errorThread.start();
        stdoutThread.start();

        try {
            // Wait for both threads indefinitely
            errorThread.join();
            stdoutThread.join();
        } catch (InterruptedException e) {
            logger.error("Client interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static void readFromPort(int port, boolean isError) {
        try {
            logger.info("Connecting to " + hostName + ":" + port + " for " + (isError ? "error" : "slow query") + " logs");
            String logFile = isError ? ERROR_LOG : SLOW_LOG;
            
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket(hostName, port);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    
                    // Rotate logs and get the current log file path
                    Path logPath = LogRotationUtil.rotateAndCleanupLogs(LOG_DIR, logFile, LOG_RETENTION_DAYS);
                    logger.info("Writing logs to: " + logPath);
                    
                    // Use try-with-resources for the file writer to ensure it's closed properly
                    try (PrintWriter writer = new PrintWriter(new FileWriter(logPath.toString(), true))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String logEntry = line;
                            
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