package org.davidgeorgehope;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

public class UserSessionManager {
    private static final Map<String, String> activeUsers = new ConcurrentHashMap<>();
    private static final String[] usernames = {"alice", "bob", "charlie", "david", "emma", "frank", "grace", "henry"};
    private Map<String, String> userSessions = new HashMap<>();
    public static String currentIP = "";

    public String getOrCreateActiveUser(String ip) {
        return activeUsers.computeIfAbsent(ip, k -> LogGeneratorUtils.getRandomElement(usernames));
    }

    public String getSessionUsername(String ip) {
        if (AnomalyConfig.isInduceHighRequestRateFromSingleIP() ||
            AnomalyConfig.isInduceHighDistinctURLsFromSingleIP()) {

            ip = LogGeneratorUtils.anomalousHighRequestIP;
            currentIP = ip;

            // For simplicity, return "-" to indicate unauthenticated user
            return "-";
        }

        // Store the current IP for other uses
        currentIP = ip;

        // Existing logic
        return userSessions.getOrDefault(ip, "-");
    }
}
