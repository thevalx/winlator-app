#!/usr/bin/env python3
"""Exercise production per-profile menu mappings and controller menu input isolation."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "app/src/main/java/com/winlator"


def production_method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


STUBS = {
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    "android/view/KeyEvent.java": """package android.view;
public class KeyEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_UNKNOWN=0,
        KEYCODE_DPAD_UP=19,KEYCODE_DPAD_DOWN=20,KEYCODE_DPAD_LEFT=21,KEYCODE_DPAD_RIGHT=22;
    private final int action,code,repeat,deviceId;
    private final boolean canceled;
    public KeyEvent(int action,int code,int repeat,int deviceId) {
        this(action,code,repeat,deviceId,false);
    }
    public KeyEvent(int action,int code,int repeat,int deviceId,boolean canceled) {
        this.action=action; this.code=code; this.repeat=repeat; this.deviceId=deviceId; this.canceled=canceled;
    }
    public int getAction() { return action; }
    public int getKeyCode() { return code; }
    public int getRepeatCount() { return repeat; }
    public int getDeviceId() { return deviceId; }
    public boolean isCanceled() { return canceled; }
    public InputDevice getDevice() { return new InputDevice(); }
    public static String keyCodeToString(int code) { return "KEYCODE_"+code; }
}
""",
    "android/view/InputDevice.java": "package android.view; public class InputDevice {}",
    "android/view/MotionEvent.java": """package android.view;
public class MotionEvent {
    public static final int AXIS_X=0,AXIS_Y=1,AXIS_Z=11,AXIS_RZ=14,AXIS_HAT_X=15,AXIS_HAT_Y=16;
}
""",
    "com/winlator/xserver/Pointer.java": """package com.winlator.xserver;
public class Pointer { public enum Button {
    BUTTON_LEFT,BUTTON_MIDDLE,BUTTON_RIGHT,BUTTON_SCROLL_UP,BUTTON_SCROLL_DOWN
} }
""",
    "com/winlator/inputcontrols/ExternalController.java": """package com.winlator.inputcontrols;
import java.util.*;
public class ExternalController {
    public final Map<Integer,ExternalControllerBinding> mappings=new HashMap<>();
    public ExternalControllerBinding getControllerBinding(int code) { return mappings.get(code); }
    public static boolean isGameController(android.view.InputDevice device) { return device!=null; }
}
""",
    "com/winlator/inputcontrols/ControlsProfile.java": """package com.winlator.inputcontrols;
import java.util.*;
public class ControlsProfile {
    public final Map<Integer,ExternalController> controllers=new HashMap<>();
    public ExternalController getController(int deviceId) { return controllers.get(deviceId); }
}
""",
    "org/json/JSONException.java": "package org.json; public class JSONException extends Exception {}",
    "org/json/JSONObject.java": """package org.json;
import java.util.*;
public class JSONObject {
    private final Map<String,Object> values=new HashMap<>();
    public void put(String key,Object value) throws JSONException { values.put(key,value); }
    public Object get(String key) { return values.get(key); }
}
""",
}


with tempfile.TemporaryDirectory(prefix="winlator-menu-binding-") as temporary:
    source = Path(temporary) / "src"
    for name, contents in STUBS.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    input_view = (PRODUCTION / "widget/InputControlsView.java").read_text()
    fixture = """import android.view.KeyEvent;
import com.winlator.inputcontrols.*;
import java.util.*;
class MenuInputFixture {
    boolean editMode;
    ControlsProfile profile;
    final Set<Long> pressedMenuKeys=new HashSet<>();
    final Set<Long> pendingMenuReleases=new HashSet<>();
    Runnable onOpenMenu;
""" + "\n".join(production_method(input_view, signature) for signature in (
        "public void setOnOpenMenu(Runnable callback)",
        "public boolean handleMenuKeyEvent(KeyEvent event)",
        "public void onInputFocusLost()",
    )) + "\n}\n"
    (source / "MenuInputFixture.java").write_text(fixture)
    classes = Path(temporary) / "classes"
    compiler = ["javac"] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    subprocess.run([
        *compiler, "-d", str(classes), *map(str, source.rglob("*.java")),
        str(PRODUCTION / "core/ControllerMenuInputGate.java"),
        str(PRODUCTION / "inputcontrols/Binding.java"),
        str(PRODUCTION / "inputcontrols/ExternalControllerBinding.java"),
        str(PRODUCTION / "xserver/XKeycode.java"),
        str(ROOT / "tests/menu_binding/MenuBindingTest.java"),
    ], check=True)
    subprocess.run(["java", "-cp", str(classes), "MenuBindingTest"], check=True)
