package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Process;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.HudMetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Lightweight HUD: X11 supplies FPS; a single background task samples sensors at 1 Hz. */
public class PerformanceHudView extends LinearLayout {
    public static final String PREF_ENABLED = "hud_enabled";
    public static final String PREF_STYLE = "hud_style";
    private static final String[] METRIC_KEYS = {
        "fps", "renderer", "gpu_usage", "gpu_name", "cpu_usage", "cpu_temp", "ram", "power", "battery_temp"
    };
    private static final int[] METRIC_LABELS = {
        R.string.hud_fps, R.string.hud_renderer, R.string.hud_gpu_usage, R.string.hud_gpu_name,
        R.string.hud_cpu_usage, R.string.hud_cpu_temperature, R.string.hud_ram,
        R.string.hud_power, R.string.hud_battery_temperature
    };
    private static final boolean[] METRIC_DEFAULTS = {true, false, true, false, true, true, true, true, true};
    private static final int WHITE = Color.rgb(237, 241, 247);
    private static final int ACCENT = Color.rgb(139, 190, 250);
    private final SharedPreferences preferences;
    private final TextView strip;
    private final boolean[] shown = new boolean[METRIC_KEYS.length];
    private boolean containerHudEnabled;
    private boolean resumed;
    private volatile boolean modernStyle;
    private ScheduledExecutorService sampler;
    private int generation;
    private float fps = Float.NaN;
    private long lastFpsUpdateTime;
    private String rendererInfo;
    private Sample sample = new Sample();

    public PerformanceHudView(Context context, SharedPreferences preferences) {
        super(context);
        this.preferences = preferences;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(5), dp(2), dp(5), dp(2));
        setBackgroundColor(0x77000000);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        strip = new TextView(context);
        strip.setTextColor(WHITE);
        strip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        strip.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        // The entire strip scales to narrow displays instead of clipping the first/last metric.
        strip.setSingleLine(true);
        strip.setAutoSizeTextTypeUniformWithConfiguration(7, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        addView(strip, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        refreshSettings();
    }

    /** Until explicitly changed, keep the container's existing HUD enabled/disabled setting. */
    public boolean isEnabled(boolean containerHudEnabled) {
        this.containerHudEnabled = containerHudEnabled;
        return preferences.getBoolean(PREF_ENABLED, containerHudEnabled);
    }

    public boolean isModernStyle() {
        return modernStyle;
    }

    public void setFps(float fps) {
        this.fps = fps;
        lastFpsUpdateTime = SystemClock.elapsedRealtime();
        // Called at the existing FPS interval, never polls hardware per rendered frame.
        render();
    }

    public void setRendererInfo(String rendererInfo) {
        this.rendererInfo = cleanName(rendererInfo);
        render();
    }

    public void refreshSettings() {
        modernStyle = !"classic".equals(preferences.getString(PREF_STYLE, "modern"));
        for (int i = 0; i < shown.length; i++) {
            shown[i] = preferences.getBoolean(metricKey(i), METRIC_DEFAULTS[i]);
        }
        boolean visible = isEnabled(containerHudEnabled) && modernStyle;
        setVisibility(visible ? VISIBLE : GONE);
        if (resumed && visible) startSampling();
        else stopSampling();
        render();
    }

    public void start() {
        resumed = true;
        refreshSettings();
    }

    public void stop() {
        resumed = false;
        stopSampling();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void startSampling() {
        if (sampler != null) return;
        final int activeGeneration = ++generation;
        sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                runnable.run();
            }, "Winlator-HUD");
            thread.setDaemon(true);
            return thread;
        });
        final SensorReader reader = new SensorReader(getContext().getApplicationContext());
        sampler.scheduleWithFixedDelay(() -> {
            Sample next = reader.read();
            post(() -> {
                if (generation == activeGeneration && resumed && getVisibility() == VISIBLE) {
                    sample = next;
                    render();
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopSampling() {
        ++generation;
        if (sampler != null) {
            sampler.shutdownNow();
            sampler = null;
        }
    }

    private void render() {
        if (strip == null) return;
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (shown[2]) append(text, "GPU", percent(sample.gpu), 0xffd873eb);
        if (shown[4] || shown[5]) {
            String value = shown[4] ? percent(sample.cpu) : "";
            if (shown[5]) value += (value.isEmpty() ? "" : " ") + temperature(sample.cpuTemperature);
            append(text, shown[4] && sample.appCpu ? getContext().getString(R.string.hud_cpu_app_label) : "CPU", value, 0xff52d9ef);
        }
        if (shown[6]) append(text, "RAM", percent(sample.memory), 0xff8ce7b5);
        if (shown[7]) append(text, "PWR", number(sample.power, "%.1fW"), 0xffffaf49);
        if (shown[8]) append(text, "BAT", temperature(sample.batteryTemperature), 0xfff0737c);
        if (shown[0]) {
            float currentFps = HudMetrics.currentFps(fps, SystemClock.elapsedRealtime() - lastFpsUpdateTime);
            append(text, "FPS", Float.isNaN(currentFps) ? unavailable() : String.format(Locale.ENGLISH, "%.0f", currentFps), 0xffa2ef48);
        }
        if (shown[1]) append(text, "RENDERER", rendererInfo != null ? rendererInfo : unavailable(), 0xffa7b9f6);
        if (shown[3]) append(text, "GPU", sample.gpuName != null ? sample.gpuName : unavailable(), 0xffd873eb);
        strip.setText(text);
    }

    private void append(SpannableStringBuilder target, String label, String value, int color) {
        if (target.length() != 0) target.append("  |  ");
        int start = target.length();
        target.append(label);
        target.setSpan(new ForegroundColorSpan(color), start, target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        target.append(" ").append(value);
    }

    private String percent(double value) { return number(value, "%.0f%%"); }
    private String temperature(double value) { return number(value, "%.0f°C"); }
    private String number(double value, String format) {
        return Double.isNaN(value) || Double.isInfinite(value) ? unavailable() : String.format(Locale.ENGLISH, format, value);
    }
    private String unavailable() { return getContext().getString(R.string.hud_unavailable); }
    private static String metricKey(int index) { return "hud_show_" + METRIC_KEYS[index]; }

    /** Settings fit the dark in-game drawer and are independently scrollable by its parent. */
    public View createSettingsView(Runnable onSettingsChanged) {
        LinearLayout settings = new LinearLayout(getContext());
        settings.setOrientation(VERTICAL);
        settings.setPadding(0, dp(8), 0, dp(8));

        Switch enable = new Switch(getContext());
        enable.setText(R.string.hud_enable);
        enable.setTextColor(WHITE);
        enable.setTextSize(16);
        enable.setMinHeight(dp(52));
        enable.setChecked(isEnabled(containerHudEnabled));
        enable.setThumbTintList(ColorStateList.valueOf(ACCENT));
        enable.setTrackTintList(ColorStateList.valueOf(0xff223c5b));
        settings.addView(enable, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        enable.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PREF_ENABLED, checked).apply();
            settingsChanged(onSettingsChanged);
        });

        TextView appearance = label(getContext().getString(R.string.hud_appearance), 12, 0xffa8b2c0);
        appearance.setPadding(0, dp(20), 0, dp(10));
        settings.addView(appearance);
        LinearLayout styleRow = new LinearLayout(getContext());
        styleRow.setGravity(Gravity.CENTER_VERTICAL);
        styleRow.addView(label(getContext().getString(R.string.hud_style), 16, WHITE), new LayoutParams(0, dp(48), 1));
        Spinner style = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item,
                new String[]{getContext().getString(R.string.hud_modern), getContext().getString(R.string.hud_classic)}) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView)super.getView(position, convertView, parent);
                view.setTextColor(WHITE);
                view.setTextSize(16);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        style.setAdapter(adapter);
        style.setSelection(isModernStyle() ? 0 : 1, false);
        style.setBackground(rounded(0xff132235, 12, 0));
        styleRow.addView(style, new LayoutParams(dp(140), dp(48)));
        settings.addView(styleRow);
        style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = position == 0 ? "modern" : "classic";
                if (!selected.equals(preferences.getString(PREF_STYLE, "modern"))) {
                    preferences.edit().putString(PREF_STYLE, selected).apply();
                    settingsChanged(onSettingsChanged);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout checks = new LinearLayout(getContext());
        checks.setOrientation(VERTICAL);
        checks.setPadding(dp(8), dp(10), dp(8), dp(10));
        checks.setBackground(rounded(0xff0b1525, 18, ACCENT));
        LayoutParams checksParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        checksParams.topMargin = dp(18);
        settings.addView(checks, checksParams);
        for (int i = 0; i < METRIC_KEYS.length; i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            for (int j = i; j < Math.min(i + 2, METRIC_KEYS.length); j++) {
                final int index = j;
                CheckBox check = new CheckBox(getContext());
                check.setText(METRIC_LABELS[j]);
                check.setTextSize(14);
                check.setTextColor(WHITE);
                check.setMinHeight(dp(52));
                check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{ACCENT, 0xffaab8c7}));
                check.setChecked(shown[j]);
                row.addView(check, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
                check.setOnCheckedChangeListener((button, checked) -> {
                    preferences.edit().putBoolean(metricKey(index), checked).apply();
                    settingsChanged(onSettingsChanged);
                });
            }
            if (i + 1 == METRIC_KEYS.length) row.addView(new View(getContext()), new LayoutParams(0, 1, 1));
            checks.addView(row);
        }
        TextView note = label(getContext().getString(R.string.hud_metrics_note), 12, 0xffa8b2c0);
        note.setPadding(0, dp(16), 0, 0);
        settings.addView(note);
        return settings;
    }

    private void settingsChanged(Runnable callback) {
        refreshSettings();
        if (callback != null) callback.run();
    }

    private TextView label(String text, int sp, int color) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(sp);
        label.setTextColor(color);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String cleanName(String value) {
        if (value == null) return null;
        value = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return value.isEmpty() || "N/A".equals(value) ? null : value.substring(0, Math.min(64, value.length()));
    }

    private static final class Sample {
        double cpu = Double.NaN;
        double gpu = Double.NaN;
        double memory = Double.NaN;
        double cpuTemperature = Double.NaN;
        double batteryTemperature = Double.NaN;
        double power = Double.NaN;
        boolean appCpu;
        String gpuName;
    }

    /** All proc/sysfs/battery work runs only on the scheduled worker. */
    private static final class SensorReader {
        private final Context context;
        private final ActivityManager activityManager;
        private final BatteryManager batteryManager;
        private final List<File> cpuThermometers = new ArrayList<>();
        private HudMetrics.CpuTimes previousCpu;
        private long previousWall;
        private long previousAppCpu;
        private boolean discovered;
        private String gpuName;

        SensorReader(Context context) {
            this.context = context;
            activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            batteryManager = (BatteryManager)context.getSystemService(Context.BATTERY_SERVICE);
        }

        Sample read() {
            Sample result = new Sample();
            try {
                if (!discovered) discoverSensors();
                long wall = SystemClock.elapsedRealtime();
                long appCpu = Process.getElapsedCpuTime();
                HudMetrics.CpuTimes cpu = HudMetrics.parseCpuTimes(readLine(new File("/proc/stat")));
                result.cpu = HudMetrics.cpuPercent(previousCpu, cpu);
                if (cpu == null) {
                    result.appCpu = true;
                    if (previousWall > 0) result.cpu = HudMetrics.appCpuPercent(appCpu - previousAppCpu,
                            wall - previousWall, Runtime.getRuntime().availableProcessors());
                }
                previousCpu = cpu;
                previousWall = wall;
                previousAppCpu = appCpu;
                for (File file : cpuThermometers) {
                    double temperature = HudMetrics.parseThermalTemperature(readLine(file));
                    if (!Double.isNaN(temperature) && (Double.isNaN(result.cpuTemperature) || temperature > result.cpuTemperature)) {
                        result.cpuTemperature = temperature;
                    }
                }
                result.gpu = HudMetrics.parseGpuBusy(readLine(new File("/sys/class/kgsl/kgsl-3d0/gpubusy")));
                result.gpuName = gpuName;
                if (activityManager != null) {
                    ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                    activityManager.getMemoryInfo(memory);
                    result.memory = HudMetrics.memoryPercent(memory.totalMem, memory.availMem);
                }
                Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery != null) {
                    result.batteryTemperature = HudMetrics.batteryTemperature(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE));
                    if (batteryManager != null) {
                        int current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                        result.power = HudMetrics.batteryPowerWatts(current, battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                    }
                }
            }
            catch (RuntimeException ignored) {
                // Permission/vendor restrictions leave the affected measurements unavailable.
            }
            return result;
        }

        private void discoverSensors() {
            discovered = true;
            gpuName = cleanName(readLine(new File("/sys/class/kgsl/kgsl-3d0/gpu_model")));
            if (gpuName == null) {
                gpuName = cleanName(PreferenceManager.getDefaultSharedPreferences(context).getString("gpu_renderer", ""));
            }
            File[] zones = new File("/sys/class/thermal").listFiles();
            if (zones == null) return;
            for (File zone : zones) {
                if (!zone.getName().startsWith("thermal_zone")) continue;
                String type = readLine(new File(zone, "type"));
                if (type == null) continue;
                type = type.toLowerCase(Locale.ENGLISH);
                if (type.contains("cpu") && !type.contains("gpu")) cpuThermometers.add(new File(zone, "temp"));
            }
        }

        private static String readLine(File file) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                return reader.readLine();
            }
            catch (IOException | SecurityException e) { return null; }
        }
    }
}
