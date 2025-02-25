package org.davidgeorgehope.nginx.logs;

import org.davidgeorgehope.AnomalyConfig;
import org.davidgeorgehope.UserSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class AccessLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AccessLogGenerator.class);

    public static void generateAccessLogs(int logsToGenerate, String filePath, boolean isFrontend, UserSessionManager userSessionManager) {
        generateAccessLogs(logsToGenerate, filePath, isFrontend, userSessionManager, -1);
    }

    public static void generateAccessLogs(int logsToGenerate, String filePath, boolean isFrontend, UserSessionManager userSessionManager, int port) {
        // If port is specified, send logs to port only, otherwise write to file
        boolean usePortLogging = port > 0;
        
        // Only open file writer if we're not using port logging
        try (FileWriter writer = usePortLogging ? null : new FileWriter(filePath, true)) {
            for (int i = 0; i < logsToGenerate; i++) {
                AccessLogEntry entry;
                if (AnomalyConfig.isInduceDatabaseOutage()) {
                    // Generate entries with 500 status codes
                    entry = AccessLogEntry.createErrorEntry(isFrontend, userSessionManager);
                } else {
                    entry = AccessLogEntry.createRandomEntry(isFrontend, userSessionManager);
                }
                String logEntry = entry.toString();
                
                // Send to TCP port if configured, otherwise write to file
                if (usePortLogging) {
                    LogSender.sendLog(port, logEntry);
                } else {
                    writer.write(logEntry);
                }

                // If inducing high visitor rate anomaly
                if (AnomalyConfig.isInduceHighVisitorRate()) {
                    logsToGenerate *= 5; // Increase the number of logs significantly
                }
            }
        } catch (IOException e) {
            if (!usePortLogging) {
                logger.error("Error writing to access log file: " + filePath, e);
            }
        }
    }
}
