package org.davidgeorgehope;

public class AnomalyConfig {
    private static boolean induceHighVisitorRate = false;
    private static boolean induceHighErrorRate = false;
    private static boolean induceHighRequestRateFromSingleIP = false;
    private static boolean induceHighDistinctURLsFromSingleIP = false;
    
    // Getters and Setters
    public static boolean isInduceHighVisitorRate() {
        return induceHighVisitorRate;
    }

    public static void setInduceHighVisitorRate(boolean induceHighVisitorRate) {
        AnomalyConfig.induceHighVisitorRate = induceHighVisitorRate;
    }

    public static boolean isInduceHighErrorRate() {
        return induceHighErrorRate;
    }

    public static void setInduceHighErrorRate(boolean induceHighErrorRate) {
        AnomalyConfig.induceHighErrorRate = induceHighErrorRate;
    }

    public static boolean isInduceHighRequestRateFromSingleIP() {
        return induceHighRequestRateFromSingleIP;
    }

    public static void setInduceHighRequestRateFromSingleIP(boolean induceHighRequestRateFromSingleIP) {
        AnomalyConfig.induceHighRequestRateFromSingleIP = induceHighRequestRateFromSingleIP;
    }

    public static boolean isInduceHighDistinctURLsFromSingleIP() {
        return induceHighDistinctURLsFromSingleIP;
    }

    public static void setInduceHighDistinctURLsFromSingleIP(boolean induceHighDistinctURLsFromSingleIP) {
        AnomalyConfig.induceHighDistinctURLsFromSingleIP = induceHighDistinctURLsFromSingleIP;
    }
}
