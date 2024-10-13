package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class ErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ErrorLogGenerator.class);

    public static void generateErrorLogs(int logsCount, String filePath, boolean isFrontend) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            int actualLogsCount = logsCount;

            if (AnomalyConfig.isInduceDatabaseOutage()) {
                // Increase error logs during database outage
                actualLogsCount *= 10; // Increase the count by a factor
            }

            for (int i = 0; i < actualLogsCount; i++) {
                String logEntry = ErrorLogEntry.createRandomEntry(isFrontend).toString();
                writer.write(logEntry);
            }
        } catch (IOException e) {
            logger.error("Error writing error log", e);
        }
    }
}
