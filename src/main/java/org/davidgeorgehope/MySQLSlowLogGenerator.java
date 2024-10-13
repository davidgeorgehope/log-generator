package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class MySQLSlowLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLSlowLogGenerator.class);

    public static void generateSlowLogs(int logsCount, String filePath) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry = MySQLSlowLogEntry.createRandomEntry().toString();
                writer.write(logEntry);
            }
        } catch (IOException e) {
            logger.error("Error writing MySQL slow log", e);
        }
    }
}
