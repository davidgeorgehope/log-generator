package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class MySQLGeneralLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLGeneralLogGenerator.class);

    public static void generateGeneralLogs(int logsCount, String filePath) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry = MySQLGeneralLogEntry.createRandomEntry().toString();
                writer.write(logEntry);
            }
        } catch (IOException e) {
            logger.error("Error writing MySQL general log", e);
        }
    }
}
