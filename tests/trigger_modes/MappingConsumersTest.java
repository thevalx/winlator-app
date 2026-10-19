import android.view.KeyEvent;
import android.view.MotionEvent;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.inputcontrols.TriggerMode;

/** Exercises the production mapping/editor event methods compiled by run.py. */
public class MappingConsumersTest {
    private static int checks;

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    private static MotionEvent motion(float left, float right, boolean alternate) {
        MotionEvent event = new MotionEvent();
        event.axes[alternate ? MotionEvent.AXIS_BRAKE : MotionEvent.AXIS_LTRIGGER] = left;
        event.axes[alternate ? MotionEvent.AXIS_GAS : MotionEvent.AXIS_RTRIGGER] = right;
        return event;
    }

    private static KeyEvent key(boolean down) {
        return new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2);
    }

    public static void main(String[] args) {
        for (boolean alternate : new boolean[]{false, true}) {
            for (int nativeMode = 0; nativeMode <= 2; nativeMode++) {
                ExternalController nativeController = new ExternalController();
                nativeController.setTriggerType(nativeMode);
                InputControlsViewFixture view = new InputControlsViewFixture(new ExternalController());
                ControllerBindingsFixture editor = new ControllerBindingsFixture(new ExternalController());

                MotionEvent left = motion(.8f, .2f, alternate);
                check(view.onGenericMotionEvent(left), "profile consumes axis-only trigger");
                check(view.left && !view.right, "axis-only left trigger activates its mapping");
                check(editor.dispatchGenericMotionEvent(left), "editor consumes axis-only trigger");
                check(editor.bindings.contains(KeyEvent.KEYCODE_BUTTON_L2), "editor detects axis-only L2");
                check(!editor.bindings.contains(KeyEvent.KEYCODE_BUTTON_R2), "editor ignores low R2 travel");

                check(nativeController.updateStateFromMotionEvent(left), "native slot consumes same motion");
                GamepadState nativeState = nativeController.getGamepadState();
                float expected = nativeMode == TriggerMode.AS_BUTTON ? 1 : .8f;
                check(Math.abs(nativeState.triggerL - expected) < .001f, "mapping leaves native mode unchanged");
                check(nativeState.isPressed(ExternalController.IDX_BUTTON_L2) == (nativeMode != TriggerMode.AS_AXIS),
                    "mapping leaves native button policy unchanged");

                check(view.onGenericMotionEvent(motion(.2f, .8f, alternate)), "profile consumes R2 motion");
                check(!view.left && view.right, "axis motion releases L2 and activates R2 mapping");
                check(editor.dispatchGenericMotionEvent(motion(.2f, .8f, alternate)), "editor consumes R2 motion");
                check(editor.bindings.contains(KeyEvent.KEYCODE_BUTTON_R2), "editor detects axis-only R2");
                view.onGenericMotionEvent(motion(0, 0, alternate));
                check(!view.left && !view.right, "axis-only release clears mappings");

                // Profile mappings also consume duplicate Android keys directly.
                check(view.onKeyEvent(key(true)), "mapped duplicate key-down consumed");
                view.onGenericMotionEvent(left);
                check(view.left, "motion keeps mapped trigger pressed");
                view.onGenericMotionEvent(motion(0, 0, alternate));
                check(!view.left, "motion-first release clears mapping");
                check(view.onKeyEvent(key(false)), "mapped duplicate key-up consumed");
                check(!view.left, "late duplicate key-up leaves mapping released");

                view.onKeyEvent(key(true));
                view.onGenericMotionEvent(left);
                view.onKeyEvent(key(false));
                view.onGenericMotionEvent(motion(0, 0, alternate));
                check(!view.left, "key-first release clears mapping");
            }
        }
        System.out.println("Passed " + checks + " mapping/editor regression checks");
    }
}
