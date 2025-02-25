package org.davidgeorgehope.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class MySQLLogClient {
    private static final Logger logger = LoggerFactory.getLogger(MySQLLogClient.class);

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java org.davidgeorgehope.client.MySQLLogClient <error-port> <stdout-port>");
            System.exit(1);
        }

        int errorPort = Integer.parseInt(args[0]);
        int stdoutPort = Integer.parseInt(args[1]);

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
            logger.info("Connecting to port " + port + " for " + (isError ? "error" : "stdout") + " logs");
            
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket("localhost", port);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isError) {
                            System.err.println("[MySQL " + (isError ? "ERROR" : "STDOUT") + "] " + line);
                        } else {
                            System.out.println("[MySQL " + (isError ? "ERROR" : "STDOUT") + "] " + line);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading from port " + port + ", retrying in 5 seconds", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fatal error in client for port " + port, e);
        }
    }
} 