#!/usr/bin/env python3
"""Exercise production trigger code with small Android event fixtures; no SDK needed."""
from pathlib import Path
import subprocess
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    'android/util/Rational.java': '''package android.util;
public class Rational { public Rational(int numerator, int denominator) {} }''',
    'androidx/annotation/Nullable.java': 'package androidx.annotation; public @interface Nullable {}',
    'android/view/InputDevice.java': '''package android.view;
public class InputDevice {
    public static final int SOURCE_GAMEPAD=1025, SOURCE_JOYSTICK=16777232;
    public static int[] getDeviceIds() { return new int[0]; }
    public static InputDevice getDevice(int id) { return null; }
    public String getDescriptor() { return "fixture"; }
    public String getName() { return "fixture"; }
    public int getVendorId() { return 1; }
    public int getProductId() { return 1; }
    public int getSources() { return SOURCE_JOYSTICK; }
    public boolean isVirtual() { return false; }
    public MotionRange getMotionRange(int axis, int source) { return new MotionRange(); }
    public static class MotionRange { public float getFlat() { return 0; } }
}''',
    'android/view/KeyEvent.java': '''package android.view;
public class KeyEvent {
    public static final int ACTION_DOWN=0, ACTION_UP=1;
    public static final int KEYCODE_BUTTON_A=96, KEYCODE_BUTTON_B=97,
        KEYCODE_BUTTON_X=99, KEYCODE_BUTTON_Y=100, KEYCODE_BUTTON_L1=102,
        KEYCODE_BUTTON_R1=103, KEYCODE_BUTTON_L2=104, KEYCODE_BUTTON_R2=105,
        KEYCODE_BUTTON_THUMBL=106, KEYCODE_BUTTON_THUMBR=107,
        KEYCODE_BUTTON_START=108, KEYCODE_BUTTON_SELECT=109,
        KEYCODE_DPAD_UP=19, KEYCODE_DPAD_DOWN=20, KEYCODE_DPAD_LEFT=21, KEYCODE_DPAD_RIGHT=22;
    private final int action, code;
    public KeyEvent(int action, int code) { this.action=action; this.code=code; }
    public int getAction() { return action; }
    public int getKeyCode() { return code; }
}''',
    'android/view/MotionEvent.java': '''package android.view;
public class MotionEvent {
    public static final int ACTION_MOVE=2, AXIS_X=0, AXIS_Y=1, AXIS_Z=11,
        AXIS_RZ=14, AXIS_HAT_X=15, AXIS_HAT_Y=16, AXIS_LTRIGGER=17,
        AXIS_RTRIGGER=18, AXIS_GAS=22, AXIS_BRAKE=23;
    public final float[] axes = new float[24];
    public int getSource() { return InputDevice.SOURCE_JOYSTICK; }
    public int getAction() { return ACTION_MOVE; }
    public int getHistorySize() { return 0; }
    public InputDevice getDevice() { return new InputDevice(); }
    public float getAxisValue(int axis) { return axes[axis]; }
    public float getHistoricalAxisValue(int axis, int history) { return axes[axis]; }
}''',
    'com/winlator/core/ArrayUtils.java': '''package com.winlator.core;
public class ArrayUtils { public static boolean contains(int[] a, int v) {
    for (int n:a) if(n==v) return true; return false;
} }''',
    'com/winlator/inputcontrols/ControlElement.java': '''package com.winlator.inputcontrols;
public class ControlElement { public static final float STICK_DEAD_ZONE=0.15f; }''',
    'com/winlator/inputcontrols/ExternalControllerBinding.java': '''package com.winlator.inputcontrols;
public class ExternalControllerBinding {
    public int getKeyCodeForAxis() { return 0; }
    public org.json.JSONObject toJSONObject() { return null; }
}''',
    'com/winlator/inputcontrols/GamepadVibration.java': '''package com.winlator.inputcontrols;
public class GamepadVibration { public GamepadVibration(String id) {} }''',
    'com/winlator/inputcontrols/GamepadSlot.java': '''package com.winlator.inputcontrols;
public interface GamepadSlot {
    String getName(); short getVendorId(); short getProductId();
    GamepadState getGamepadState(); GamepadVibration getGamepadVibration();
}''',
    'org/json/JSONException.java': 'package org.json; public class JSONException extends Exception {}',
    'org/json/JSONObject.java': '''package org.json; public class JSONObject {
    public void put(String key,Object value) throws JSONException {}
}''',
    'org/json/JSONArray.java': '''package org.json; public class JSONArray {
    public void put(Object value) {}
}''',
}

with tempfile.TemporaryDirectory(prefix='winlator-triggers-') as temp:
    source = Path(temp) / 'src'
    for name, content in STUBS.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    production = ROOT / 'app/src/main/java/com/winlator'
    files = list(source.rglob('*.java')) + [
        production / 'inputcontrols/ExternalController.java',
        production / 'inputcontrols/GamepadState.java',
        production / 'inputcontrols/TriggerMode.java',
        production / 'math/Mathf.java',
        Path(__file__).with_name('TriggerModesTest.java'),
    ]
    classes = Path(temp) / 'classes'
    compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '-d', str(classes), *map(str, files)], check=True)
    subprocess.run(['java', '-cp', str(classes), 'TriggerModesTest'], check=True)
