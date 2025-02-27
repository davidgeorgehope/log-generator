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

public class NginxIngressLogClient {
    private static final Logger logger = LoggerFactory.getLogger(NginxIngressLogClient.class);
    private static String hostName = "localhost"; // Default to localhost if not specified
    private static final String LOG_DIR = System.getenv().getOrDefault("LOG_DIRECTORY", "/var/log/nginx_ingress");
    private static final String ACCESS_LOG = "access.log";
    private static final String ERROR_LOG = "error.log";
    private static final int LOG_RETENTION_DAYS = 1; // Keep logs for 1 day

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java org.davidgeorgehope.client.NginxIngressLogClient <ingress-access-port> [ingress-error-port] [hostname]");
            System.exit(1);
        }

        // Create final variables for use in lambda expressions
        final int ingressAccessPort = Integer.parseInt(args[0]);
        Thread ingressErrorThread = null;
        
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
        
        // Start thread to read from the ingress access port
        Thread ingressAccessThread = new Thread(() -> readFromPort(ingressAccessPort, false));
        ingressAccessThread.setDaemon(true);
        ingressAccessThread.start();
        
        // Check for error port parameter
        if (args.length >= 2 && args[1] != null && !args[1].trim().isEmpty()) {
            try {
                final int errorPort = Integer.parseInt(args[1]);
                logger.info("Using Ingress error port: " + errorPort);
                // Start thread to read from the ingress error port
                ingressErrorThread = new Thread(() -> readFromPort(errorPort, true));
                ingressErrorThread.setDaemon(true);
                ingressErrorThread.start();
            } catch (NumberFormatException e) {
                // Might be hostname, not port
                hostName = args[1].trim();
                logger.info("Using host: " + hostName);
            }
        }
        
        // Check for hostname parameter (3rd param if error port is provided)
        if (args.length >= 3 && args[2] != null && !args[2].trim().isEmpty()) {
            hostName = args[2].trim();
            logger.info("Using host: " + hostName);
        }

        try {
            // Wait for threads indefinitely
            ingressAccessThread.join();
            if (ingressErrorThread != null) {
                ingressErrorThread.join();
            }
        } catch (InterruptedException e) {
            logger.error("Client interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static void readFromPort(int port, boolean isError) {
        try {
            logger.info("Connecting to " + hostName + ":" + port + " for NGINX Ingress " + 
                      (isError ? "error" : "access") + " logs");
            String logFile = isError ? ERROR_LOG : ACCESS_LOG;
            
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
                            // Write to file
                            writer.println(line);
                            writer.flush();
                            
                            // Also output to console for debugging
                            if (isError) {
                                System.err.println(line);
                            } else {
                                System.out.println(line);
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