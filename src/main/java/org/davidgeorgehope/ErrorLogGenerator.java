package org.davidgeorgehope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class ErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ErrorLogGenerator.class);

    public static void generateErrorLogs(int count, String fileName, boolean isFrontend) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName, true))) {
            for (int i = 0; i < count; i++) {
                ErrorLogEntry logEntry = ErrorLogEntry.createRandomEntry(isFrontend);
                bw.write(logEntry.toString());
            }
        } catch (IOException e) {
            logger.error("Error writing error logs to file: {}", fileName, e);
        }
    }
}