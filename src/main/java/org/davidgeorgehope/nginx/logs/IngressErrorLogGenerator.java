package org.davidgeorgehope.nginx.logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class IngressErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(IngressErrorLogGenerator.class);

    public static void generateIngressErrorLogs(int count, String filePath, boolean isFrontend) {
        generateIngressErrorLogs(count, filePath, isFrontend, -1);
    }

    public static void generateIngressErrorLogs(int count, String filePath, boolean isFrontend, int port) {
        // If port is specified, send logs to port only, otherwise write to file
        boolean usePortLogging = port > 0;
        
        try (FileWriter writer = usePortLogging ? null : new FileWriter(filePath, true)) {
            for (int i = 0; i < count; i++) {
                IngressErrorLogEntry entry = IngressErrorLogEntry.createRandomEntry(isFrontend);
                String logEntry = entry.toString();
                
                // Send to TCP port if configured, otherwise write to file
                if (usePortLogging) {
                    LogSender.sendLog(port, logEntry);
                } else {
                    writer.write(logEntry);
                }
            }
        } catch (IOException e) {
            if (!usePortLogging) {
                logger.error("Error writing to ingress error log file: " + filePath, e);
            }
        }
    }
} 