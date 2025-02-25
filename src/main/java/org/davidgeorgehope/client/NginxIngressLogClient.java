package org.davidgeorgehope.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class NginxIngressLogClient {
    private static final Logger logger = LoggerFactory.getLogger(NginxIngressLogClient.class);
    private static String hostName = "localhost"; // Default to localhost if not specified

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java org.davidgeorgehope.client.NginxIngressLogClient <ingress-access-port> [ingress-error-port] [hostname]");
            System.exit(1);
        }

        // Create final variables for use in lambda expressions
        final int ingressAccessPort = Integer.parseInt(args[0]);
        Thread ingressErrorThread = null;
        
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
            
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket(hostName, port);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isError) {
                            System.err.println(line);
                        } else {
                            System.out.println(line);
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