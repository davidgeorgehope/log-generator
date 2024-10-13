package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class MySQLErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLErrorLogGenerator.class);

    public static void generateErrorLogs(int logsCount, String filePath) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry;
                if (AnomalyConfig.isInduceDatabaseOutage()) {
                    // Generate outage-specific error logs
                    logEntry = MySQLErrorLogEntry.createOutageEntry().toString();
                } else {
                    // Generate non-outage logs with potential low storage warnings
                    logEntry = MySQLErrorLogEntry.createRandomEntry().toString();
                }
                writer.write(logEntry);
            }
        } catch (IOException e) {
            logger.error("Error writing MySQL error log", e);
        }
    }
}
