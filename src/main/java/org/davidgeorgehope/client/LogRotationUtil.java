package org.davidgeorgehope.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * Utility class for log rotation and cleanup of old logs.
 * Supports daily rotation and retention of logs for a configurable number of days.
 */
public class LogRotationUtil {
    private static final Logger logger = LoggerFactory.getLogger(LogRotationUtil.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * Performs log rotation and cleanup:
     * 1. Rolls over the current log file to a dated version if needed
     * 2. Deletes log files older than the retention period
     * 
     * @param logDir The directory containing log files
     * @param logFileName The base log file name
     * @param retentionDays Number of days to keep logs (default is 1)
     * @return Path to the current log file to use
     */
    public static Path rotateAndCleanupLogs(String logDir, String logFileName, int retentionDays) {
        if (retentionDays < 1) {
            retentionDays = 1; // Ensure minimum retention of 1 day
        }
        
        Path logDirPath = Paths.get(logDir);
        Path currentLogPath = logDirPath.resolve(logFileName);
        
        try {
            // Create directory if it doesn't exist
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
                logger.info("Created log directory: {}", logDir);
                return currentLogPath; // New directory, no need for rotation yet
            }
            
            // Rotate current log file if it exists and is from a previous day
            if (Files.exists(currentLogPath)) {
                LocalDate today = LocalDate.now();
                
                BasicFileAttributes attrs = Files.readAttributes(currentLogPath, BasicFileAttributes.class);
                LocalDateTime fileCreationTime = LocalDateTime.ofInstant(
                        attrs.creationTime().toInstant(), ZoneId.systemDefault());
                LocalDate fileDate = fileCreationTime.toLocalDate();
                
                // If log file is from a previous day, rotate it
                if (fileDate.isBefore(today)) {
                    String dateSuffix = fileDate.format(DATE_FORMATTER);
                    Path rotatedPath = logDirPath.resolve(logFileName + "." + dateSuffix);
                    
                    // If the rotated file already exists, append content instead of overwriting
                    if (Files.exists(rotatedPath)) {
                        // Append current log to rotated log
                        Files.write(rotatedPath, Files.readAllBytes(currentLogPath),
                                java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        // Move the current log to the rotated file
                        Files.move(currentLogPath, rotatedPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    logger.info("Rotated log file {} to {}", logFileName, rotatedPath.getFileName());
                }
            }
            
            // Delete old log files beyond retention period
            deleteOldLogs(logDirPath, logFileName, retentionDays);
            
            // Create a new log file if it doesn't exist
            if (!Files.exists(currentLogPath)) {
                Files.createFile(currentLogPath);
            }
            
            return currentLogPath;
            
        } catch (IOException e) {
            logger.error("Error during log rotation for {}: {}", logFileName, e.getMessage(), e);
            return currentLogPath; // Return the default path even if rotation failed
        }
    }
    
    /**
     * Deletes log files that are older than the specified retention period
     * 
     * @param logDirPath Path to the log directory
     * @param logFileName Base name of the log file
     * @param retentionDays Number of days to keep logs
     */
    private static void deleteOldLogs(Path logDirPath, String logFileName, int retentionDays) {
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
            
            // Find and delete old rotated log files
            try (Stream<Path> files = Files.list(logDirPath)) {
                files.filter(path -> {
                    String fileName = path.getFileName().toString();
                    // Only consider files that match our pattern: logFileName.yyyy-MM-dd
                    return fileName.startsWith(logFileName + ".") && 
                           fileName.length() == logFileName.length() + 11; // +11 for ".yyyy-MM-dd"
                }).forEach(path -> {
                    try {
                        // Extract date from filename
                        String dateStr = path.getFileName().toString().substring(logFileName.length() + 1);
                        LocalDate fileDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                        
                        // Delete if older than retention period
                        if (fileDate.isBefore(cutoffDate)) {
                            Files.delete(path);
                            logger.info("Deleted old log file: {}", path.getFileName());
                        }
                    } catch (Exception e) {
                        logger.warn("Error processing log file {}: {}", path, e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            logger.error("Error cleaning up old logs: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Shorthand method to rotate logs with default retention of 1 day
     */
    public static Path rotateAndCleanupLogs(String logDir, String logFileName) {
        return rotateAndCleanupLogs(logDir, logFileName, 1); // Default 1 day retention
    }
} 