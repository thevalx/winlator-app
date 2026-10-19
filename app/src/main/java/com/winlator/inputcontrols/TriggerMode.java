package com.winlator.inputcontrols;

/** Values persisted as trigger_type (global) and triggerType (shortcut). */
public final class TriggerMode {
    public static final int AS_BUTTON = 0;
    public static final int AS_AXIS = 1;
    public static final int BOTH = 2;
    public static final int DEFAULT = AS_AXIS;

    private TriggerMode() {}

    public static int normalize(int value) {
        return value >= AS_BUTTON && value <= BOTH ? value : DEFAULT;
    }

    public static int resolve(String override, int global) {
        if (override != null) {
            try {
                int value = Integer.parseInt(override);
                if (value >= AS_BUTTON && value <= BOTH) return value;
            }
            catch (NumberFormatException ignored) {}
        }
        return normalize(global);
    }
}
