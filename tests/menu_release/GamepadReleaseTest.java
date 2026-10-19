package com.winlator.winhandler;

import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.GamepadSlot;
import com.winlator.inputcontrols.GamepadState;

import java.util.HashSet;
import java.util.Set;

public class GamepadReleaseTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void pressAll(GamepadState state) {
        state.buttons = (short)0xffff;
        state.thumbLX = state.thumbLY = state.thumbRX = state.thumbRY = 1;
        state.triggerL = state.triggerR = 1;
        for (int i = 0; i < state.dpad.length; i++) state.dpad[i] = true;
    }

    private static void checkNeutral(GamepadState state, String message) {
        check(state.buttons == 0 && state.thumbLX == 0 && state.thumbLY == 0 &&
                state.thumbRX == 0 && state.thumbRY == 0 && state.triggerL == 0 &&
                state.triggerR == 0 && state.getPovHat() == -1, message);
    }

    private static GamepadHandlerFixture fixture(int controllerCount, boolean virtual) {
        GamepadHandlerFixture handler = new GamepadHandlerFixture();
        for (int i = 0; i < controllerCount; i++) {
            ExternalController controller = new ExternalController();
            pressAll(controller.state);
            handler.connectedControllers.add(controller);
            handler.gamepadSlots[i] = controller;
        }
        if (virtual) {
            ControlsProfile profile = new ControlsProfile();
            pressAll(profile.state);
            handler.winHandler.activity.input.profile = profile;
            handler.gamepadSlots[3] = profile;
        }
        handler.gamepadClients.add(101);
        handler.gamepadClients.add(202);
        return handler;
    }

    private static void checkNeutralPacket(Packet packet, byte mapper) {
        int expectedLength = mapper == GamepadHandlerFixture.DINPUT_MAPPER_TYPE_XINPUT ? 17 : 13;
        check(packet.data.length == expectedLength, "Packet retains existing mapper wire format");
        check(packet.data[0] == RequestCodes.GET_GAMEPAD_STATE, "Release uses existing state request");
        check(packet.data[2] == 0 && packet.data[3] == 0, "All digital buttons released");
        check(packet.data[4] == -1, "D-pad is neutral");
        for (int i = 5; i < packet.data.length; i++) {
            check(packet.data[i] == 0, "Stick and trigger bytes are neutral at index " + i);
        }
    }

    private static void allSlotsReleaseDespiteQueuedOrdinaryState(byte mapper) {
        GamepadHandlerFixture handler = fixture(3, true);
        handler.dinputMapperType = mapper;
        handler.sendGamepadState(handler.gamepadSlots[0]);
        check(handler.sendingState.get(), "An ordinary state transmission remains queued");
        check(handler.winHandler.queue.size() == 2, "Ordinary state queued for both clients");
        handler.releaseAllControllerInputs();
        check(handler.winHandler.queue.size() == 10, "Release queued for every slot/client despite busy sendingState");
        check(handler.sendingState.get(), "Release does not disturb ordinary send backpressure");

        for (ExternalController controller : handler.connectedControllers) {
            check(controller.resets == 1, "Each physical controller reset exactly once");
            checkNeutral(controller.state, "Physical state immediately cleared");
        }
        checkNeutral(handler.winHandler.activity.input.profile.state, "Virtual state immediately cleared");

        // The action queue is intentionally delayed; subsequent presses must not overwrite releases.
        for (GamepadSlot slot : handler.gamepadSlots) pressAll(slot.getGamepadState());
        handler.winHandler.flush();
        check(handler.winHandler.packets.size() == 10, "All queued packets transmitted");
        Set<String> released = new HashSet<>();
        for (int i = 2; i < handler.winHandler.packets.size(); i++) {
            Packet packet = handler.winHandler.packets.get(i);
            checkNeutralPacket(packet, mapper);
            released.add(packet.port + ":" + packet.data[1]);
        }
        check(released.size() == 8, "Each of four slots released for each client");
        for (int slot = 0; slot < 4; slot++) {
            check(released.contains("101:" + slot) && released.contains("202:" + slot),
                    "Slot " + slot + " received by both clients");
        }
    }

    private static void sparseSlotsRemainCorrect() {
        GamepadHandlerFixture handler = fixture(1, true);
        handler.releaseAllControllerInputs();
        handler.winHandler.flush();
        check(handler.winHandler.packets.size() == 4, "Unassigned slots emit no packets");
        for (Packet packet : handler.winHandler.packets) {
            check(packet.data[1] == 0 || packet.data[1] == 3, "Release retains sparse assigned slot index");
            checkNeutralPacket(packet, handler.dinputMapperType);
        }
    }

    private static void unavailableTransportStillResetsLocally(boolean initialized) {
        GamepadHandlerFixture handler = fixture(2, true);
        handler.winHandler.initReceived = initialized;
        if (initialized) handler.gamepadClients.clear();
        handler.releaseAllControllerInputs();
        check(handler.winHandler.queue.isEmpty(), "Unavailable transport does not enqueue packets");
        for (ExternalController controller : handler.connectedControllers) {
            check(controller.resets == 1, "Physical controller resets without transport");
            checkNeutral(controller.state, "Physical state cleared without transport");
        }
        checkNeutral(handler.winHandler.activity.input.profile.state, "Virtual state cleared without transport");
    }

    public static void main(String[] args) {
        allSlotsReleaseDespiteQueuedOrdinaryState(GamepadHandlerFixture.DINPUT_MAPPER_TYPE_XINPUT);
        allSlotsReleaseDespiteQueuedOrdinaryState(GamepadHandlerFixture.DINPUT_MAPPER_TYPE_STANDARD);
        sparseSlotsRemainCorrect();
        unavailableTransportStillResetsLocally(false);
        unavailableTransportStillResetsLocally(true);
        GamepadHandlerFixture empty = fixture(0, false);
        empty.releaseAllControllerInputs();
        check(empty.winHandler.queue.isEmpty(), "No controller/profile requires no release packet");
        System.out.println(checks + " queued controller menu-release checks passed");
    }
}
