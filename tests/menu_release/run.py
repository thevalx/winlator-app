#!/usr/bin/env python3
"""Exercise actual menu-release and packet methods against a delayed WinHandler queue."""
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
    "com/winlator/inputcontrols/GamepadSlot.java": """package com.winlator.inputcontrols;
public interface GamepadSlot { GamepadState getGamepadState(); }
""",
    "com/winlator/inputcontrols/ExternalController.java": """package com.winlator.inputcontrols;
public class ExternalController implements GamepadSlot {
    public static final byte IDX_BUTTON_L2=10, IDX_BUTTON_R2=11;
    public final GamepadState state=new GamepadState();
    public int resets;
    public GamepadState getGamepadState() { return state; }
    public void resetState() { state.copy(new GamepadState()); resets++; }
}
""",
    "com/winlator/inputcontrols/ControlsProfile.java": """package com.winlator.inputcontrols;
public class ControlsProfile implements GamepadSlot {
    public final GamepadState state=new GamepadState();
    public GamepadState getGamepadState() { return state; }
}
""",
    "com/winlator/core/ArrayUtils.java": """package com.winlator.core;
public class ArrayUtils {
    public static final int INDEX_NOT_FOUND=-1;
    public static int indexOf(Object[] values,Object value) {
        for(int i=0;i<values.length;i++) if(values[i]==value) return i;
        return INDEX_NOT_FOUND;
    }
}
""",
}


def fixture_source(source):
    methods = "\n".join(production_method(source, signature) for signature in (
        "private void writeStateToBuffer(ByteBuffer buffer, GamepadState state)",
        "public void sendGamepadState(final GamepadSlot gamepadSlot)",
        "public void releaseAllControllerInputs()",
    ))
    return """package com.winlator.winhandler;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import com.winlator.core.ArrayUtils;
import com.winlator.inputcontrols.*;

class GamepadHandlerFixture {
    static final byte DINPUT_MAPPER_TYPE_STANDARD=0, DINPUT_MAPPER_TYPE_XINPUT=1;
    static final byte GAMEPAD_MAX_COUNT=4;
    final WinHandler winHandler=new WinHandler();
    final List<Integer> gamepadClients=new CopyOnWriteArrayList<>();
    final GamepadSlot[] gamepadSlots=new GamepadSlot[GAMEPAD_MAX_COUNT];
    final ArrayList<ExternalController> connectedControllers=new ArrayList<>();
    final AtomicBoolean sendingState=new AtomicBoolean();
    byte dinputMapperType=DINPUT_MAPPER_TYPE_XINPUT;
""" + methods + """
}

class WinHandler {
    boolean initReceived=true;
    final ActivityFixture activity=new ActivityFixture();
    final ByteBuffer sendData=ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
    final ArrayDeque<Runnable> queue=new ArrayDeque<>();
    final ArrayList<Packet> packets=new ArrayList<>();
    void addAction(Runnable action) { queue.add(action); }
    void sendPacket(int port) {
        packets.add(new Packet(port,Arrays.copyOf(sendData.array(),sendData.position())));
    }
    void flush() { while(!queue.isEmpty()) queue.remove().run(); }
}
class ActivityFixture {
    final InputFixture input=new InputFixture();
    InputFixture getInputControlsView() { return input; }
}
class InputFixture {
    ControlsProfile profile;
    ControlsProfile getProfile() { return profile; }
}
class Packet {
    final int port;
    final byte[] data;
    Packet(int port,byte[] data) { this.port=port; this.data=data; }
}
"""


with tempfile.TemporaryDirectory(prefix="winlator-menu-release-") as temporary:
    source = Path(temporary) / "src"
    for name, contents in STUBS.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    handler = (PRODUCTION / "winhandler/GamepadHandler.java").read_text()
    (source / "GamepadHandlerFixture.java").write_text(fixture_source(handler))
    classes = Path(temporary) / "classes"
    compiler = ["javac"] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    subprocess.run([
        *compiler, "-d", str(classes), *map(str, source.rglob("*.java")),
        str(PRODUCTION / "inputcontrols/GamepadState.java"),
        str(PRODUCTION / "winhandler/RequestCodes.java"),
        str(Path(__file__).with_name("GamepadReleaseTest.java")),
    ], check=True)
    subprocess.run(["java", "-cp", str(classes), "com.winlator.winhandler.GamepadReleaseTest"], check=True)
