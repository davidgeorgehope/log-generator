package org.davidgeorgehope.nginx.logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class for sending log messages to TCP ports
 */
public class LogSender {
    private static final Logger logger = LoggerFactory.getLogger(LogSender.class);
    private static final Map<Integer, ServerSocket> portToServerSocket = new ConcurrentHashMap<>();
    private static final Map<Integer, ExecutorService> portToExecutorService = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<Socket, PrintWriter>> portToClients = new ConcurrentHashMap<>();

    /**
     * Initialize a server socket for a specific port
     * @param port The port to initialize
     */
    public static void initializePort(int port) {
        if (portToServerSocket.containsKey(port)) {
            // Already initialized
            return;
        }

        try {
            ServerSocket serverSocket = new ServerSocket(port);
            portToServerSocket.put(port, serverSocket);
            portToClients.put(port, new ConcurrentHashMap<>());
            
            // Create an executor for handling client connections
            ExecutorService executor = Executors.newCachedThreadPool();
            portToExecutorService.put(port, executor);
            
            // Start a thread to accept client connections
            executor.submit(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        logger.info("Client connected to port " + port);
                        portToClients.get(port).put(client, new PrintWriter(client.getOutputStream(), true));
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            logger.error("Error accepting client connection on port " + port, e);
                        }
                    }
                }
            });
            
            logger.info("Initialized log server on port " + port);
        } catch (IOException e) {
            logger.error("Failed to initialize server socket for port " + port, e);
        }
    }
    
    /**
     * Send a log message to a specific port
     * @param port The port to send the log to
     * @param message The log message to send
     */
    public static void sendLog(int port, String message) {
        if (port <= 0) {
            // Port not configured, skip
            return;
        }
        
        Map<Socket, PrintWriter> clients = portToClients.get(port);
        if (clients == null || clients.isEmpty()) {
            // No clients connected, skip
            return;
        }
        
        // Send to all connected clients
        clients.entrySet().removeIf(entry -> {
            try {
                PrintWriter writer = entry.getValue();
                writer.println(message);
                if (writer.checkError()) {
                    throw new IOException("Error writing to client");
                }
                return false;
            } catch (Exception e) {
                try {
                    entry.getKey().close();
                } catch (IOException ex) {
                    // Ignore
                }
                return true;
            }
        });
    }
    
    /**
     * Shutdown all server sockets and client connections
     */
    public static void shutdown() {
        for (Map.Entry<Integer, ServerSocket> entry : portToServerSocket.entrySet()) {
            try {
                Integer port = entry.getKey();
                logger.info("Shutting down log server on port " + port);
                
                // Close all client connections
                Map<Socket, PrintWriter> clients = portToClients.get(port);
                if (clients != null) {
                    for (Socket client : clients.keySet()) {
                        try {
                            client.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                    clients.clear();
                }
                
                // Close server socket
                entry.getValue().close();
                
                // Shutdown executor
                ExecutorService executor = portToExecutorService.get(port);
                if (executor != null) {
                    executor.shutdownNow();
                }
            } catch (IOException e) {
                logger.error("Error shutting down server socket", e);
            }
        }
        
        portToServerSocket.clear();
        portToExecutorService.clear();
        portToClients.clear();
    }
} 