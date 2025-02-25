package org.davidgeorgehope.mysql;

import org.davidgeorgehope.nginx.logs.LogSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class MySQLSlowLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLSlowLogGenerator.class);

    public static void generateSlowLogs(int logsCount, String filePath) {
        generateSlowLogs(logsCount, filePath, -1);
    }

    public static void generateSlowLogs(int logsCount, String filePath, int port) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry = MySQLSlowLogEntry.createRandomEntry().toString();
                writer.write(logEntry);
                
                // Send to TCP port if configured
                if (port > 0) {
                    LogSender.sendLog(port, logEntry);
                }
                
                // Introduce a slight delay if desired
                // Thread.sleep(50);
            }
        } catch (IOException e) {
            logger.error("Error writing to MySQL slow log file: " + filePath, e);
        }
    }
}
