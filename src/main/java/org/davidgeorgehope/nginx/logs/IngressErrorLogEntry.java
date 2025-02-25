package org.davidgeorgehope.nginx.logs;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.davidgeorgehope.LogEntry;

public class IngressErrorLogEntry extends LogEntry {
    // Format matches the expected pattern in the Elastic ingest pipeline for Nginx Ingress Controller error logs
    // Example: E0225 14:10:44.455123 12345 controller.go:142] Error message here
    
    private static final String[] LOG_LEVELS = {"I", "W", "E", "F"};  // Info, Warning, Error, Fatal
    private static final String[] ERROR_MESSAGES = {
        "Error obtaining Ingress information: ingresses.networking.k8s.io is forbidden",
        "Error creating SSL certificate: tls: failed to verify certificate: x509: certificate has expired",
        "Error reading SSL certificate: open /etc/ingress-controller/ssl/default-fake-certificate.pem: no such file or directory",
        "Error starting NGINX: exit status 1",
        "Error getting SSL certificate \"default/tls-secret\": secret default/tls-secret was not found",
        "Error reloading NGINX: exit status 1",
        "Error updating Ingress status: Operation cannot be fulfilled on ingresses.networking.k8s.io",
        "Error validating SSL certificate: x509: certificate signed by unknown authority",
        "Error obtaining endpoints: endpoints \"default/backend\" not found",
        "Error obtaining service: services \"default/backend\" not found",
        "Error creating temp file: permission denied",
        "Error reading configuration template: open /etc/nginx/template/nginx.tmpl: no such file or directory",
        "Error updating ingress status: Operation cannot be fulfilled on ingresses.extensions",
        "Error getting secret \"default/tls-secret\": secret \"default/tls-secret\" not found"
    };
    
    private static final String[] SOURCE_FILES = {
        "controller.go",
        "store.go",
        "nginx.go",
        "backend_ssl.go",
        "command.go",
        "annotations.go",
        "ingress.go",
        "endpointslices.go",
        "status.go",
        "configmap.go",
        "parser.go",
        "utils.go"
    };
    
    private final String logLevel;                // log.level
    private final int timestampMonth;             // timestamp_month
    private final int timestampDay;               // timestamp_day
    private final int timestampHour;              // timestamp_hour
    private final int timestampMinute;            // timestamp_minute
    private final int timestampSecond;            // timestamp_second
    private final int timestampNano;              // timestamp_nano
    private final int threadId;                   // nginx_ingress_controller.error.thread_id
    private final String sourceFile;              // nginx_ingress_controller.error.source.file
    private final int sourceLineNumber;           // nginx_ingress_controller.error.source.line_number
    private final String message;                 // message

    private IngressErrorLogEntry(
            String logLevel,
            int timestampMonth,
            int timestampDay,
            int timestampHour,
            int timestampMinute,
            int timestampSecond,
            int timestampNano,
            int threadId,
            String sourceFile,
            int sourceLineNumber,
            String message) {
        this.logLevel = logLevel;
        this.timestampMonth = timestampMonth;
        this.timestampDay = timestampDay;
        this.timestampHour = timestampHour;
        this.timestampMinute = timestampMinute;
        this.timestampSecond = timestampSecond;
        this.timestampNano = timestampNano;
        this.threadId = threadId;
        this.sourceFile = sourceFile;
        this.sourceLineNumber = sourceLineNumber;
        this.message = message;
    }

    public static IngressErrorLogEntry createRandomEntry(boolean isFrontend) {
        Random random = new Random();
        
        // Get current time components
        ZonedDateTime now = ZonedDateTime.now();
        int timestampMonth = now.getMonthValue();
        int timestampDay = now.getDayOfMonth();
        int timestampHour = now.getHour();
        int timestampMinute = now.getMinute();
        int timestampSecond = now.getSecond();
        int timestampNano = random.nextInt(1000000); // 6 digits for microseconds
        
        // Determine log level - bias toward info and warning
        String logLevel;
        double levelRandom = random.nextDouble();
        if (levelRandom < 0.6) {
            logLevel = "I"; // Info - 60%
        } else if (levelRandom < 0.85) {
            logLevel = "W"; // Warning - 25%
        } else if (levelRandom < 0.97) {
            logLevel = "E"; // Error - 12%
        } else {
            logLevel = "F"; // Fatal - 3%
        }
        
        // Thread ID between 1 and 99999
        int threadId = random.nextInt(99999) + 1;
        
        // Source file and line number
        String sourceFile = SOURCE_FILES[random.nextInt(SOURCE_FILES.length)];
        int sourceLineNumber = random.nextInt(1000) + 1;
        
        // Error message
        String message = ERROR_MESSAGES[random.nextInt(ERROR_MESSAGES.length)];
        
        return new IngressErrorLogEntry(
                logLevel,
                timestampMonth,
                timestampDay,
                timestampHour,
                timestampMinute,
                timestampSecond,
                timestampNano,
                threadId,
                sourceFile,
                sourceLineNumber,
                message);
    }

    @Override
    public String toString() {
        // Format: E0225 14:10:44.455123 12345 controller.go:142] Error message here
        return String.format("%s%02d%02d %02d:%02d:%02d.%06d %d %s:%d] %s",
                logLevel,
                timestampMonth,
                timestampDay,
                timestampHour,
                timestampMinute,
                timestampSecond,
                timestampNano,
                threadId,
                sourceFile,
                sourceLineNumber,
                message);
    }
} 