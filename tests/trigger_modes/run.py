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
    public int getDeviceId() { return -1; }
    public int getRepeatCount() { return 0; }
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
    public int getDeviceId() { return -1; }
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
    private int keyCode;
    private Binding binding;
    public int getKeyCodeForAxis() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
    public Binding getBinding() { return binding; }
    public void setBinding(Binding binding) { this.binding = binding; }
    public org.json.JSONObject toJSONObject() { return null; }
}''',
    'com/winlator/inputcontrols/Binding.java': '''package com.winlator.inputcontrols;
public enum Binding { NONE, KEY_A, KEY_B }''',
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


def production_method(path, signature):
    """Compile the actual event consumers with fixtures for unrelated Android UI."""
    source = path.read_text()
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def mapping_fixtures(production):
    view = production / 'widget/InputControlsView.java'
    editor = production / 'ExternalControllerBindingsActivity.java'
    return '''import android.view.KeyEvent;
import android.view.MotionEvent;
import com.winlator.inputcontrols.*;
class MappingBase {
    public boolean onGenericMotionEvent(MotionEvent e) { return false; }
    public boolean dispatchGenericMotionEvent(MotionEvent e) { return false; }
    public boolean dispatchKeyEvent(KeyEvent e) { return false; }
}
class ProfileFixture {
    final ExternalController controller;
    ProfileFixture(ExternalController controller) { this.controller = controller; }
    ExternalController getController(int id) { return controller; }
}
class InputControlsViewFixture extends MappingBase {
    boolean editMode;
    ProfileFixture profile;
    boolean left, right;
    InputControlsViewFixture(ExternalController controller) {
        profile = new ProfileFixture(controller);
        for (int code : new int[]{KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2}) {
            ExternalControllerBinding binding = new ExternalControllerBinding();
            binding.setKeyCode(code);
            binding.setBinding(code == KeyEvent.KEYCODE_BUTTON_L2 ? Binding.KEY_A : Binding.KEY_B);
            controller.addControllerBinding(binding);
        }
    }
    void handleInputEvent(Binding binding, boolean pressed) {
        if (binding == Binding.KEY_A) left = pressed;
        if (binding == Binding.KEY_B) right = pressed;
    }
    void processJoystickInput(ExternalController controller) {}
''' + production_method(view, 'public boolean onGenericMotionEvent(MotionEvent event)') + '\n' + production_method(view, 'public boolean onKeyEvent(KeyEvent event)') + '''
}
class ControllerBindingsFixture extends MappingBase {
    ExternalController controller;
    java.util.Set<Integer> bindings = new java.util.HashSet<>();
    ControllerBindingsFixture(ExternalController controller) { this.controller = controller; }
    void updateControllerBinding(int keyCode, Binding binding) { bindings.add(keyCode); }
    void processJoystickInput() {}
''' + production_method(editor, 'public boolean dispatchGenericMotionEvent(MotionEvent event)') + '\n' + production_method(editor, 'public boolean dispatchKeyEvent(KeyEvent event)') + '\n}\n'


with tempfile.TemporaryDirectory(prefix='winlator-triggers-') as temp:
    source = Path(temp) / 'src'
    for name, content in STUBS.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    production = ROOT / 'app/src/main/java/com/winlator'
    (source / 'MappingConsumers.java').write_text(mapping_fixtures(production))
    files = list(source.rglob('*.java')) + [
        production / 'inputcontrols/ExternalController.java',
        production / 'inputcontrols/GamepadState.java',
        production / 'inputcontrols/TriggerMode.java',
        production / 'math/Mathf.java',
        Path(__file__).with_name('TriggerModesTest.java'),
        Path(__file__).with_name('MappingConsumersTest.java'),
    ]
    classes = Path(temp) / 'classes'
    compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '-d', str(classes), *map(str, files)], check=True)
    subprocess.run(['java', '-cp', str(classes), 'TriggerModesTest'], check=True)
    subprocess.run(['java', '-cp', str(classes), 'MappingConsumersTest'], check=True)
