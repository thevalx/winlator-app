package com.winlator.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Spinner;

import androidx.recyclerview.widget.RecyclerView;

import com.winlator.ExternalControllerBindingsActivity;
import com.winlator.R;
import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalControllerBinding;
import com.winlator.inputcontrols.InputControlsManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS-w1280dp-h591dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControllerMenuBindingTest {
    private static final int PROFILE_ID = 125;
    private static final String CONTROLLER_ID = "gamesir-test-controller";
    private Context context;
    private ControlsProfile profile;
    private InputControlsView inputs;
    private final AtomicInteger opens = new AtomicInteger();

    @Before public void setUp() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.AppThemeDark);
        context = activity;
        writeProfile(Binding.NONE);
        profile = loadProfile();
        inputs = new InputControlsView(activity);
        inputs.setProfile(profile);
        inputs.setOnOpenMenu(opens::incrementAndGet);
    }

    @Test public void unboundModeButtonDoesNotOpenMenuByDefault() {
        assertFalse(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertFalse(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_UP, 0)));
        assertEquals(0, opens.get());
        assertEquals(Binding.NONE, profile.getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).getBinding());
    }

    @Test public void explicitlyMappedModeButtonFiresOncePerPressAndConsumesRelease() {
        bindMenu();
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 1)));
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(1, opens.get());
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_UP, 0)));
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(2, opens.get());
    }

    @Test public void ordinaryGamepadAndKeyboardMappingsStillReachNormalInputHandling() {
        profile.getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).setBinding(Binding.KEY_ENTER);
        assertFalse(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        profile.getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).setBinding(Binding.GAMEPAD_BUTTON_START);
        assertFalse(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(0, opens.get());
    }

    @Test public void editingProfileDoesNotActivateMappedMenuAction() {
        bindMenu();
        inputs.setEditMode(true);
        assertFalse(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(0, opens.get());
    }

    @Test public void canceledMappedPressIsConsumedWithoutOpeningMenu() {
        bindMenu();
        KeyEvent canceled = KeyEvent.changeFlags(key(KeyEvent.ACTION_DOWN, 0), KeyEvent.FLAG_CANCELED);
        assertTrue(inputs.handleMenuKeyEvent(canceled));
        assertEquals(0, opens.get());
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(1, opens.get());
    }

    @Test public void releasedMenuKeyIsConsumedEvenIfProfileWasChangedWhileHeld() {
        bindMenu();
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        inputs.setProfile(null);
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_UP, 0)));
        assertFalse(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(1, opens.get());
    }

    @Test public void savedMenuMappingSurvivesProfileReload() throws Exception {
        bindMenu();
        profile.save();
        ControlsProfile restored = loadProfile();
        assertEquals("Saved profile: " + new String(Files.readAllBytes(ControlsProfile.getProfileFile(context, PROFILE_ID).toPath()), StandardCharsets.UTF_8),
                Binding.WINLATOR_MENU, restored.getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).getBinding());
        inputs.setProfile(restored);
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(1, opens.get());
        assertEquals(Binding.WINLATOR_MENU, restored.getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).getBinding());
    }

    @Test public void losingInputFocusSuppressesHeldRepeatsAndRecoversOnFreshPress() {
        bindMenu();
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        inputs.onInputFocusLost();
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 1)));
        assertEquals(1, opens.get());
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(2, opens.get());
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_UP, 0)));
        assertTrue(inputs.handleMenuKeyEvent(key(KeyEvent.ACTION_DOWN, 0)));
        assertEquals(3, opens.get());
    }

    @Test public void virtualMenuBindingInvokesCallbackOnceUntilReleased() {
        inputs.handleInputEvent(Binding.WINLATOR_MENU, true);
        inputs.handleInputEvent(Binding.WINLATOR_MENU, true);
        assertEquals(1, opens.get());
        inputs.handleInputEvent(Binding.WINLATOR_MENU, false);
        inputs.handleInputEvent(Binding.WINLATOR_MENU, true);
        assertEquals(2, opens.get());
        inputs.releaseActiveInputs();
        inputs.handleInputEvent(Binding.WINLATOR_MENU, true);
        assertEquals(3, opens.get());
    }

    @Test public void existingControllerEditorOffersWinlatorCategoryAndPersistsChosenAction() throws Exception {
        ExternalControllerBindingsActivity editor = openEditor();
        RecyclerView list = editor.findViewById(R.id.RecyclerView);
        UiTestSupport.layout(list, 1120, 460);
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull("Mode button row must be visible in the existing controller editor", holder);
        Spinner category = holder.itemView.findViewById(R.id.SBindingType);
        Spinner action = holder.itemView.findViewById(R.id.SBinding);
        assertEquals("Keyboard", category.getSelectedItem().toString());
        assertEquals(Binding.NONE, loadProfile().getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).getBinding());
        selectItem(category, "Winlator");
        UiTestSupport.layout(list, 1120, 460);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("NONE", action.getSelectedItem().toString());
        assertEquals(Binding.NONE, loadProfile().getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).getBinding());
        selectItem(action, "Open in-game menu");
        UiTestSupport.layout(list, 1120, 460);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Open in-game menu", action.getSelectedItem().toString());
        assertEquals(Binding.WINLATOR_MENU, loadProfile().getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE).getBinding());
        editor.finish();

        ExternalControllerBindingsActivity reopened = openEditor();
        RecyclerView reopenedList = reopened.findViewById(R.id.RecyclerView);
        UiTestSupport.layout(reopenedList, 1120, 460);
        shadowOf(Looper.getMainLooper()).idle();
        View row = reopenedList.findViewHolderForAdapterPosition(0).itemView;
        assertEquals("Winlator", ((Spinner)row.findViewById(R.id.SBindingType)).getSelectedItem().toString());
        assertEquals("Open in-game menu", ((Spinner)row.findViewById(R.id.SBinding)).getSelectedItem().toString());
        UiTestSupport.exportPreview(reopened.findViewById(android.R.id.content), "controller-menu-binding.png", 1280, 591);
        reopened.finish();
    }

    private void bindMenu() {
        ExternalControllerBinding binding = profile.getController(CONTROLLER_ID).getControllerBinding(KeyEvent.KEYCODE_BUTTON_MODE);
        binding.setBinding(Binding.WINLATOR_MENU);
    }

    private ExternalControllerBindingsActivity openEditor() {
        Intent intent = new Intent(context, ExternalControllerBindingsActivity.class)
                .putExtra("profile_id", PROFILE_ID).putExtra("controller_id", CONTROLLER_ID);
        return Robolectric.buildActivity(ExternalControllerBindingsActivity.class, intent).setup().get();
    }

    private ControlsProfile loadProfile() {
        return InputControlsManager.loadProfile(context, ControlsProfile.getProfileFile(context, PROFILE_ID));
    }

    private void writeProfile(Binding binding) throws Exception {
        JSONObject mode = new JSONObject().put("keyCode", KeyEvent.KEYCODE_BUTTON_MODE).put("binding", binding.name());
        JSONObject controller = new JSONObject().put("id", CONTROLLER_ID).put("name", "GameSir-G8+")
                .put("controllerBindings", new JSONArray().put(mode));
        JSONObject data = new JSONObject().put("id", PROFILE_ID).put("name", "UI test controller profile")
                .put("cursorSpeed", 1.0)
                .put("elements", new JSONArray()).put("controllers", new JSONArray().put(controller));
        Files.write(ControlsProfile.getProfileFile(context, PROFILE_ID).toPath(), data.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static KeyEvent key(int action, int repeat) {
        return new KeyEvent(100, 100, action, KeyEvent.KEYCODE_BUTTON_MODE, repeat, 0,
                -1, 0, 0, InputDevice.SOURCE_GAMEPAD);
    }

    private static void selectItem(Spinner spinner, String label) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (label.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
                return;
            }
        }
        fail("Missing binding category: " + label);
    }
}
