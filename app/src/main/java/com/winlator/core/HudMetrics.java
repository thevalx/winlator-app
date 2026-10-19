package com.winlator.core;

/** Parsers and calculations for real HUD measurements. Missing readings remain NaN. */
public final class HudMetrics {
    private HudMetrics() {}

    public static final class CpuTimes {
        public final long total;
        public final long idle;

        private CpuTimes(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
    }

    /** Linux guest counters are already included in user/nice and must not be counted twice. */
    public static CpuTimes parseCpuTimes(String line) {
        if (line == null) return null;
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 5 || !"cpu".equals(fields[0])) return null;
        long total = 0;
        long idle = 0;
        try {
            for (int i = 1; i < Math.min(fields.length, 9); i++) {
                long value = Long.parseLong(fields[i]);
                if (value < 0 || Long.MAX_VALUE - total < value) return null;
                total += value;
                if (i == 4 || i == 5) idle += value;
            }
        }
        catch (NumberFormatException e) { return null; }
        return total == 0 ? null : new CpuTimes(total, idle);
    }

    public static double cpuPercent(CpuTimes previous, CpuTimes current) {
        if (previous == null || current == null) return Double.NaN;
        long total = current.total - previous.total;
        long idle = current.idle - previous.idle;
        if (total <= 0 || idle < 0 || idle > total) return Double.NaN;
        return (total - idle) * 100.0 / total;
    }

    /** App-process CPU, normalized to total device CPU capacity; never labeled total CPU. */
    public static double appCpuPercent(long elapsedCpuMs, long elapsedWallMs, int cpuCount) {
        if (elapsedCpuMs < 0 || elapsedWallMs <= 0 || cpuCount <= 0) return Double.NaN;
        return Math.min(100.0, elapsedCpuMs * 100.0 / elapsedWallMs / cpuCount);
    }

    public static double memoryPercent(long totalBytes, long availableBytes) {
        if (totalBytes <= 0 || availableBytes < 0 || availableBytes > totalBytes) return Double.NaN;
        return (totalBytes - availableBytes) * 100.0 / totalBytes;
    }

    /** KGSL exposes busy and total time for its measured interval. */
    public static double parseGpuBusy(String value) {
        if (value == null) return Double.NaN;
        String[] fields = value.trim().split("\\s+");
        if (fields.length != 2) return Double.NaN;
        try {
            long busy = Long.parseLong(fields[0]);
            long total = Long.parseLong(fields[1]);
            if (busy < 0 || total <= 0 || busy > total) return Double.NaN;
            return busy * 100.0 / total;
        }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    /** The Linux thermal-zone temperature ABI is millidegrees Celsius. */
    public static double parseThermalTemperature(String value) {
        if (value == null) return Double.NaN;
        try {
            double temperature = Double.parseDouble(value.trim()) / 1000.0;
            return isTemperature(temperature) ? temperature : Double.NaN;
        }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    public static double batteryTemperature(int deciCelsius) {
        double temperature = deciCelsius / 10.0;
        return deciCelsius != Integer.MIN_VALUE && isTemperature(temperature) ? temperature : Double.NaN;
    }

    /** BatteryManager's documented microamperes and ACTION_BATTERY_CHANGED millivolts. */
    public static double batteryPowerWatts(int currentMicroamperes, int voltageMillivolts) {
        if (currentMicroamperes == Integer.MIN_VALUE || voltageMillivolts <= 0) return Double.NaN;
        double watts = Math.abs((double)currentMicroamperes) / 1000000.0 * voltageMillivolts / 1000.0;
        // A broken/vendor-specific reading must not become an enormous "measurement".
        return watts <= 200 ? watts : Double.NaN;
    }

    /** A measured frame rate becomes zero when frames stop; a missing X11 window stays N/A. */
    public static float currentFps(float measuredFps, long ageMillis) {
        if (Float.isNaN(measuredFps) || Float.isInfinite(measuredFps) || measuredFps < 0 || ageMillis < 0) {
            return Float.NaN;
        }
        return ageMillis > 1500 ? 0 : measuredFps;
    }

    private static boolean isTemperature(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= -20 && value <= 150;
    }
}
