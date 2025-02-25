package org.davidgeorgehope.mysql;

import org.davidgeorgehope.nginx.logs.LogSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class MySQLGeneralLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLGeneralLogGenerator.class);

    public static void generateGeneralLogs(int logsCount, String filePath) {
        generateGeneralLogs(logsCount, filePath, -1);
    }

    public static void generateGeneralLogs(int logsCount, String filePath, int port) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry = MySQLGeneralLogEntry.createRandomEntry().toString();
                writer.write(logEntry);
                
                // Send to TCP port if configured
                if (port > 0) {
                    LogSender.sendLog(port, logEntry);
                }
            }
        } catch (IOException e) {
            logger.error("Error writing to MySQL general log file: " + filePath, e);
        }
    }
}
