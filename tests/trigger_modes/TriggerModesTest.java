import android.view.KeyEvent;
import android.view.MotionEvent;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.inputcontrols.TriggerMode;

public class TriggerModesTest {
    private static int checks;

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    private static void close(float actual, float expected) {
        check(Math.abs(actual - expected) < 0.001f, actual + " != " + expected);
    }

    private static void motion(ExternalController c, float left, float right, boolean alternate) {
        MotionEvent e = new MotionEvent();
        e.axes[alternate ? MotionEvent.AXIS_BRAKE : MotionEvent.AXIS_LTRIGGER] = left;
        e.axes[alternate ? MotionEvent.AXIS_GAS : MotionEvent.AXIS_RTRIGGER] = right;
        check(c.updateStateFromMotionEvent(e), "motion consumed");
    }

    private static void key(ExternalController c, boolean left, boolean down) {
        check(c.updateStateFromKeyEvent(new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
            left ? KeyEvent.KEYCODE_BUTTON_L2 : KeyEvent.KEYCODE_BUTTON_R2)), "key consumed");
    }

    public static void main(String[] args) {
        check(TriggerMode.DEFAULT == TriggerMode.AS_AXIS, "axis default");
        for (int global = 0; global <= 2; global++) {
            check(TriggerMode.resolve(null, global) == global, "null inherits");
            for (String invalid : new String[]{"", "bad", "-1", "3", "999999999999"})
                check(TriggerMode.resolve(invalid, global) == global, "invalid inherits");
            for (int mode = 0; mode <= 2; mode++)
                check(TriggerMode.resolve(String.valueOf(mode), global) == mode, "override wins");
        }
        check(TriggerMode.resolve(null, 99) == TriggerMode.AS_AXIS, "invalid global defaults");

        for (int mode = 0; mode <= 2; mode++) {
            for (boolean alternate : new boolean[]{false, true}) {
                ExternalController c = new ExternalController();
                c.setTriggerType(mode);
                GamepadState s = c.getGamepadState();
                motion(c, .25f, .75f, alternate);
                close(s.triggerL, mode == TriggerMode.AS_BUTTON ? 0 : .25f);
                close(s.triggerR, mode == TriggerMode.AS_BUTTON ? 1 : .75f);
                check(s.isPressed(ExternalController.IDX_BUTTON_R2) == (mode != TriggerMode.AS_AXIS), "axis-derived bit");
                key(c, true, true);
                key(c, false, true);
                close(s.triggerL, mode == TriggerMode.AS_BUTTON ? 1 : .25f);
                close(s.triggerR, mode == TriggerMode.AS_BUTTON ? 1 : .75f);
                motion(c, .4f, .6f, alternate);
                close(s.triggerL, mode == TriggerMode.AS_BUTTON ? 1 : .4f);
                close(s.triggerR, mode == TriggerMode.AS_BUTTON ? 1 : .6f);
                check(s.isPressed(ExternalController.IDX_BUTTON_L2) == (mode != TriggerMode.AS_AXIS), "retain key bit");
                // Key release first: preserve the remaining analog travel.
                key(c, true, false);
                key(c, false, false);
                close(s.triggerL, mode == TriggerMode.AS_BUTTON ? 0 : .4f);
                close(s.triggerR, mode == TriggerMode.AS_BUTTON ? 1 : .6f);
                motion(c, 0, 0, alternate);
                close(s.triggerL, 0); close(s.triggerR, 0);
                check(s.buttons == 0, "release clears buttons");
                // Motion release first: duplicate key-up must also clear both triggers.
                key(c, true, true); key(c, false, true);
                motion(c, .8f, .9f, alternate);
                motion(c, 0, 0, alternate);
                key(c, true, false); key(c, false, false);
                close(s.triggerL, 0); close(s.triggerR, 0);
                check(s.buttons == 0, "reverse release clears buttons");
                // A key event must never disable subsequent analog motion.
                motion(c, .15f, .35f, alternate);
                close(s.triggerL, mode == TriggerMode.AS_BUTTON ? 0 : .15f);
                close(s.triggerR, mode == TriggerMode.AS_BUTTON ? 0 : .35f);
                motion(c, -1, 2, alternate);
                close(s.triggerL, 0); close(s.triggerR, 1);
                c.updateStateFromKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A));
                check(s.isPressed(ExternalController.IDX_BUTTON_A), "ordinary buttons unchanged");
                c.setTriggerType((mode + 1) % 3);
                close(s.triggerL, 0); close(s.triggerR, 0);
                check(!s.isPressed(ExternalController.IDX_BUTTON_R2), "mode change clears trigger");
                check(s.isPressed(ExternalController.IDX_BUTTON_A), "mode change preserves ordinary buttons");
            }
        }
        ExternalController digital = new ExternalController();
        digital.setTriggerType(TriggerMode.AS_BUTTON);
        key(digital, true, true); close(digital.getGamepadState().triggerL, 1);
        key(digital, true, false); close(digital.getGamepadState().triggerL, 0);
        for (int mode = 0; mode <= 2; mode++) {
            ExternalController c = new ExternalController();
            c.setTriggerType(mode);
            motion(c, .8f, .9f, false);
            key(c, true, true); key(c, false, true);
            GamepadState state = c.getGamepadState();
            state.thumbLX = .7f; state.thumbLY = -.6f;
            state.thumbRX = -.3f; state.thumbRY = .4f;
            state.dpad[0] = state.dpad[2] = true;
            c.resetState();
            check(state.buttons == 0, "menu release neutralizes buttons");
            close(state.triggerL, 0); close(state.triggerR, 0);
            close(state.thumbLX, 0); close(state.thumbLY, 0);
            close(state.thumbRX, 0); close(state.thumbRY, 0);
            check(state.getPovHat() == -1, "menu release neutralizes D-pad");
            // Late trigger-up after focus loss cannot resurrect the previous raw travel.
            key(c, true, false); key(c, false, false);
            close(state.triggerL, 0); close(state.triggerR, 0);
            motion(c, .25f, .75f, false);
            close(state.triggerL, mode == TriggerMode.AS_BUTTON ? 0 : .25f);
            close(state.triggerR, mode == TriggerMode.AS_BUTTON ? 1 : .75f);
        }
        GamepadState virtual = new GamepadState();
        virtual.setPressed(ExternalController.IDX_BUTTON_L2, true); close(virtual.triggerL, 1);
        virtual.setPressed(ExternalController.IDX_BUTTON_L2, false); close(virtual.triggerL, 0);
        System.out.println("Passed " + checks + " trigger regression checks");
    }
}
