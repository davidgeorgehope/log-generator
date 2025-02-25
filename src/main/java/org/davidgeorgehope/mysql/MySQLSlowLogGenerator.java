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
        // If port is specified, send logs to port only, otherwise write to file
        boolean usePortLogging = port > 0;
        
        try (FileWriter writer = usePortLogging ? null : new FileWriter(filePath, true)) {
            for (int i = 0; i < logsCount; i++) {
                String logEntry = MySQLSlowLogEntry.createRandomEntry().toString();
                
                // Send to TCP port if configured, otherwise write to file
                if (usePortLogging) {
                    LogSender.sendLog(port, logEntry);
                } else {
                    writer.write(logEntry);
                }
                
                // Introduce a slight delay if desired
                // Thread.sleep(50);
            }
        } catch (IOException e) {
            if (!usePortLogging) {
                logger.error("Error writing to MySQL slow log file: " + filePath, e);
            }
        }
    }
}
