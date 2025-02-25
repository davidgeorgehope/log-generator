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
        try (FileWriter writer = new FileWriter(filePath, true)) {
            int actualLogsCount = logsToGenerate;

            if (AnomalyConfig.isInduceDatabaseOutage()) {
                // Increase error logs during database outage
                actualLogsCount *= 10; // Increase the count by a factor
            }

            for (int i = 0; i < actualLogsCount; i++) {
                ErrorLogEntry entry = ErrorLogEntry.createRandomEntry(isFrontend);
                String logEntry = entry.toString();
                writer.write(logEntry);
                
                // Send to TCP port if configured
                if (port > 0) {
                    LogSender.sendLog(port, logEntry);
                }
            }
        } catch (IOException e) {
            logger.error("Error writing to error log file: " + filePath, e);
        }
    }
}
