package org.davidgeorgehope.nginx.logs;

import org.davidgeorgehope.AnomalyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class ErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ErrorLogGenerator.class);

    public static void generateErrorLogs(int logsToGenerate, String filePath, boolean isFrontend) {
        generateErrorLogs(logsToGenerate, filePath, isFrontend, -1);
    }

    public static void generateErrorLogs(int logsToGenerate, String filePath, boolean isFrontend, int port) {
        // If port is specified, send logs to port only, otherwise write to file
        boolean usePortLogging = port > 0;
        
        try (FileWriter writer = usePortLogging ? null : new FileWriter(filePath, true)) {
            int actualLogsCount = logsToGenerate;

            if (AnomalyConfig.isInduceDatabaseOutage()) {
                // Increase error logs during database outage
                actualLogsCount *= 10; // Increase the count by a factor
            }

            for (int i = 0; i < actualLogsCount; i++) {
                ErrorLogEntry entry = ErrorLogEntry.createRandomEntry(isFrontend);
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
                logger.error("Error writing to error log file: " + filePath, e);
            }
        }
    }
}
