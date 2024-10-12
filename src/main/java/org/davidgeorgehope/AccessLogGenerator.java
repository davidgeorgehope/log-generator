package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class AccessLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AccessLogGenerator.class);

    public static void generateAccessLogs(int count, String fileName, boolean isFrontend, UserSessionManager userSessionManager) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName, true))) {
            for (int i = 0; i < count; i++) {
                AccessLogEntry logEntry = AccessLogEntry.createRandomEntry(isFrontend, userSessionManager);
                bw.write(logEntry.toString());
            }
        } catch (IOException e) {
            logger.error("Error writing access logs to file: {}", fileName, e);
        }
    }
}