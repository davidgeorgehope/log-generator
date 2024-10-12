package org.davidgeorgehope;

public class AnomalyConfig {
    private static boolean induceHighVisitorRate = false;
    private static boolean induceHighErrorRate = false;
    private static boolean induceHighRequestRateFromSingleIP = false;
    private static boolean induceHighDistinctURLsFromSingleIP = false;
    private static boolean induceLowRequestRate = false;
    
    // Getters and Setters
    public static boolean isInduceHighVisitorRate() {
        return induceHighVisitorRate;
    }

    public static void setInduceHighVisitorRate(boolean value) {
        induceHighVisitorRate = value;
    }

    public static boolean isInduceHighErrorRate() {
        return induceHighErrorRate;
    }

    public static void setInduceHighErrorRate(boolean value) {
        induceHighErrorRate = value;
    }

    public static boolean isInduceHighRequestRateFromSingleIP() {
        return induceHighRequestRateFromSingleIP;
    }

    public static void setInduceHighRequestRateFromSingleIP(boolean value) {
        induceHighRequestRateFromSingleIP = value;
    }

    public static boolean isInduceHighDistinctURLsFromSingleIP() {
        return induceHighDistinctURLsFromSingleIP;
    }

    public static void setInduceHighDistinctURLsFromSingleIP(boolean value) {
        induceHighDistinctURLsFromSingleIP = value;
    }

    public static boolean isInduceLowRequestRate() {
        return induceLowRequestRate;
    }

    public static void setInduceLowRequestRate(boolean value) {
        induceLowRequestRate = value;
    }
}
