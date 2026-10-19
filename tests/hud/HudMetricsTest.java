import com.winlator.core.HudMetrics;

public class HudMetricsTest {
    private static int checks;

    private static void near(double actual, double expected, String description) {
        checks++;
        if (Double.isNaN(actual) || Math.abs(actual - expected) > 0.0001) {
            throw new AssertionError(description + ": got " + actual + ", expected " + expected);
        }
    }

    private static void unavailable(double value, String description) {
        checks++;
        if (!Double.isNaN(value)) throw new AssertionError(description + ": expected unavailable, got " + value);
    }

    private static void badCpu(String value) {
        checks++;
        if (HudMetrics.parseCpuTimes(value) != null) throw new AssertionError("Invalid CPU input accepted: " + value);
    }

    public static void main(String[] args) {
        HudMetrics.CpuTimes before = HudMetrics.parseCpuTimes("cpu  100 20 30 200 10 0 0 0 60 0");
        HudMetrics.CpuTimes after = HudMetrics.parseCpuTimes("cpu  120 20 40 260 20 0 0 0 90 0");
        near(HudMetrics.cpuPercent(before, after), 30, "total CPU from actual busy/idle deltas; excludes guest double counting");
        near(HudMetrics.cpuPercent(HudMetrics.parseCpuTimes("cpu 0 0 0 1"),
                HudMetrics.parseCpuTimes("cpu 0 0 0 10")), 0, "idle is a legitimate zero");
        near(HudMetrics.cpuPercent(HudMetrics.parseCpuTimes("cpu 1 0 0 0"),
                HudMetrics.parseCpuTimes("cpu 2 0 0 0")), 100, "fully busy CPU");
        unavailable(HudMetrics.cpuPercent(null, after), "first sample not fabricated");
        unavailable(HudMetrics.cpuPercent(before, null), "denied proc read");
        unavailable(HudMetrics.cpuPercent(before, before), "zero interval");
        unavailable(HudMetrics.cpuPercent(after, before), "counter reset");
        unavailable(HudMetrics.cpuPercent(HudMetrics.parseCpuTimes("cpu 100 0 0 10"),
                HudMetrics.parseCpuTimes("cpu 90 0 0 30")), "invalid idle interval");
        for (String input : new String[]{null, "", "cpu0 1 2 3 4", "cpu 1 2 3", "cpu 0 0 0 0", "cpu a 2 3 4", "cpu -1 2 3 4", "cpu 9223372036854775807 1 0 0", "cpu 999999999999999999999 0 0 0"}) badCpu(input);

        near(HudMetrics.appCpuPercent(4000, 1000, 8), 50, "app CPU normalized to all eight cores");
        near(HudMetrics.appCpuPercent(0, 1000, 8), 0, "idle app CPU");
        near(HudMetrics.appCpuPercent(9000, 1000, 8), 100, "rounding does not exceed 100 percent");
        unavailable(HudMetrics.appCpuPercent(-1, 1000, 8), "negative app delta");
        unavailable(HudMetrics.appCpuPercent(1, 0, 8), "zero app interval");
        unavailable(HudMetrics.appCpuPercent(1, 1000, 0), "unknown core count");

        near(HudMetrics.memoryPercent(16000, 4000), 75, "memory from actual available and total");
        near(HudMetrics.memoryPercent(16000, 16000), 0, "no memory used");
        unavailable(HudMetrics.memoryPercent(0, 0), "unknown RAM");
        unavailable(HudMetrics.memoryPercent(10, -1), "bad available RAM");
        unavailable(HudMetrics.memoryPercent(10, 11), "available above total");

        near(HudMetrics.parseGpuBusy("123 456"), 12300.0 / 456, "KGSL busy/total interval");
        near(HudMetrics.parseGpuBusy(" 0\t100\n"), 0, "idle GPU remains zero");
        near(HudMetrics.parseGpuBusy("100 100"), 100, "fully busy GPU");
        for (String input : new String[]{null, "", "0 0", "100 10", "-1 100", "1 -100", "abc 100", "20", "20 %", "20 100 7"}) unavailable(HudMetrics.parseGpuBusy(input), "invalid or inaccessible GPU: " + input);

        near(HudMetrics.parseThermalTemperature("62000"), 62, "CPU thermal ABI millidegrees");
        near(HudMetrics.parseThermalTemperature("-5000"), -5, "valid cold sensor");
        for (String input : new String[]{null, "", "NaN", "Infinity", "unknown", "151000", "-21000"}) unavailable(HudMetrics.parseThermalTemperature(input), "unavailable CPU temperature: " + input);
        near(HudMetrics.batteryTemperature(387), 38.7, "battery tenths of a degree");
        unavailable(HudMetrics.batteryTemperature(Integer.MIN_VALUE), "battery temperature missing extra");
        unavailable(HudMetrics.batteryTemperature(1510), "invalid battery sensor");
        near(HudMetrics.batteryPowerWatts(-1250000, 4000), 5, "battery discharge units and sign");
        near(HudMetrics.batteryPowerWatts(1250000, 4000), 5, "battery charge magnitude");
        near(HudMetrics.batteryPowerWatts(0, 4000), 0, "legitimate zero battery current");
        near(HudMetrics.batteryPowerWatts(900, 4000), .0036, "never guess milliamperes for a small microamp reading");
        unavailable(HudMetrics.batteryPowerWatts(Integer.MIN_VALUE, 4000), "unsupported BatteryManager property");
        unavailable(HudMetrics.batteryPowerWatts(1000, -1), "unknown battery voltage");
        unavailable(HudMetrics.batteryPowerWatts(Integer.MAX_VALUE, 4000), "invalid vendor current");
        near(HudMetrics.currentFps(60, 0), 60, "fresh FPS reading");
        near(HudMetrics.currentFps(60, 1500), 60, "FPS grace period");
        near(HudMetrics.currentFps(60, 1501), 0, "stopped frames show zero rather than stale FPS");
        near(HudMetrics.currentFps(0, 2000), 0, "zero FPS remains valid");
        unavailable(HudMetrics.currentFps(Float.NaN, 0), "missing window clears FPS");
        unavailable(HudMetrics.currentFps(Float.NaN, 2000), "missing window does not become zero FPS");
        unavailable(HudMetrics.currentFps(Float.POSITIVE_INFINITY, 0), "invalid FPS unavailable");
        unavailable(HudMetrics.currentFps(-1, 0), "negative FPS unavailable");
        unavailable(HudMetrics.currentFps(60, -1), "clock reset unavailable");
        System.out.println(checks + " HUD telemetry checks passed");
    }
}
