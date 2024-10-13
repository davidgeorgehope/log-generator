package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class MySQLErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLErrorLogGenerator.class);
    private static final DateTimeFormatter ERROR_LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd HH:mm:ss");

    public static void generateErrorLogs(int logsCount, String filePath) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry;
                if (AnomalyConfig.isInduceDatabaseOutage()) {
                    // Generate outage-specific error logs
                    logEntry = MySQLErrorLogEntry.createOutageEntry().toString();
                } else {
                    logEntry = MySQLErrorLogEntry.createRandomEntry().toString();
                }
                writer.write(logEntry);
            }
        } catch (IOException e) {
            logger.error("Error writing MySQL error log", e);
        }
    }
}
