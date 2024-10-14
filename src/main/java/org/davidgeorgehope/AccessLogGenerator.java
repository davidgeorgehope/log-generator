package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class AccessLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AccessLogGenerator.class);

    public static void generateAccessLogs(int logsToGenerate, String filePath, boolean isFrontend, UserSessionManager userSessionManager) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsToGenerate; i++) {
                AccessLogEntry entry;
                if (AnomalyConfig.isInduceDatabaseOutage()) {
                    // Generate entries with 500 status codes
                    entry = AccessLogEntry.createErrorEntry(isFrontend, userSessionManager);
                } else {
                    entry = AccessLogEntry.createRandomEntry(isFrontend, userSessionManager);
                }
                writer.write(entry.toString());

                // If inducing high visitor rate anomaly
                if (AnomalyConfig.isInduceHighVisitorRate()) {
                    logsToGenerate *= 5; // Increase the number of logs significantly
                }
            }
        } catch (IOException e) {
            logger.error("Error writing access log", e);
        }
    }
}
