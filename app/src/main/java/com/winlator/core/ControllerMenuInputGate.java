package com.winlator.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Keeps held controller keys from leaking across the Android menu/game boundary. */
public final class ControllerMenuInputGate {
    private final Set<Long> gamePresses = new HashSet<>();
    private final Set<Long> suppressedPresses = new HashSet<>();

    public void onMenuOpened() {
        suppressedPresses.addAll(gamePresses);
        gamePresses.clear();
    }

    public void onFocusLost() { onMenuOpened(); }

    public boolean shouldBlock(int action, int keyCode, int repeatCount, int deviceId,
                               boolean controllerEvent, boolean menuOpen) {
        if (!controllerEvent || deviceId < 0) return false;
        long press = (((long)deviceId) << 32) ^ (keyCode & 0xffffffffL);
        if (menuOpen) {
            if (action == 0) suppressedPresses.add(press);
            else if (action == 1) suppressedPresses.remove(press);
            gamePresses.remove(press);
            return true;
        }
        if (suppressedPresses.contains(press)) {
            if (action == 1) suppressedPresses.remove(press);
            else if (action == 0 && repeatCount == 0) suppressedPresses.remove(press);
            else return true;
            if (action != 0) return true;
        }
        if (action == 0) {
            if (repeatCount > 0 && !gamePresses.contains(press)) return true;
            gamePresses.add(press);
        }
        else if (action == 1) gamePresses.remove(press);
        return false;
    }

    public void removeDevice(int deviceId) {
        removeDevice(gamePresses, deviceId);
        removeDevice(suppressedPresses, deviceId);
    }

    public void reset() {
        gamePresses.clear();
        suppressedPresses.clear();
    }

    private static void removeDevice(Set<Long> presses, int deviceId) {
        Iterator<Long> iterator = presses.iterator();
        while (iterator.hasNext()) {
            if ((int)(iterator.next() >> 32) == deviceId) iterator.remove();
        }
    }
}
