package com.winlator.widget;

import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.winhandler.ProcessInfo;
import com.winlator.winhandler.WinHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS-w1280dp-h591dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class InGameMenuViewTest {
    /** Attach the actual activity context without starting Wine, GL, sockets or native services. */
    public static class SessionActivity extends XServerDisplayActivity {
        final RecordingWinHandler handler = new RecordingWinHandler(this);
        @Override public void onCreate(Bundle state) { setTheme(R.style.AppThemeFullscreenDark); }
        @Override public WinHandler getWinHandler() { return handler; }
    }

    private static class RecordingWinHandler extends WinHandler {
        int requests;
        RecordingWinHandler(XServerDisplayActivity activity) { super(activity); }
        @Override public void listProcesses() { requests++; }
    }

    private SessionActivity activity;
    private InGameMenuView drawer;
    private Menu menu;
    private final ArrayList<Integer> selectedActions = new ArrayList<>();

    @Before public void setUp() {
        activity = Robolectric.buildActivity(SessionActivity.class).create().get();
        menu = new PopupMenu(activity, new View(activity)).getMenu();
        activity.getMenuInflater().inflate(R.menu.xserver_menu, menu);
        drawer = new InGameMenuView(activity, menu, item -> { selectedActions.add(item.getItemId()); return true; });
    }

    @After public void tearDown() { drawer.onMenuClosed(); }

    @Test public void officialSessionActionsAreRoutedThroughOriginalMenuItems() {
        clickAction(R.id.menu_item_keyboard);
        clickAction(R.id.menu_item_toggle_fullscreen);
        assertEquals(java.util.Arrays.asList(R.id.menu_item_keyboard, R.id.menu_item_toggle_fullscreen), selectedActions);
        select(R.string.ingame_settings);
        clickAction(R.id.menu_item_screen_effect);
        clickAction(R.id.menu_item_exit);
        assertEquals((Integer)R.id.menu_item_exit, selectedActions.get(selectedActions.size() - 1));
    }

    @Test public void inputHasProfileBindingHelpAndHudEmbedsSettingsAcrossRepeatedSwitches() {
        TextView hud = new TextView(activity);
        hud.setText("HUD settings test");
        drawer.setHudSettingsView(hud);
        select(R.string.ingame_input);
        assertNotNull(UiTestSupport.text(drawer, activity.getString(R.string.ingame_controller_help)));
        clickAction(R.id.menu_item_input_controls);
        select(R.string.ingame_hud);
        assertSame(hud, UiTestSupport.text(drawer, "HUD settings test"));
        select(R.string.ingame_input);
        assertNotNull(UiTestSupport.text(drawer, activity.getString(R.string.ingame_controller_help)));
        assertEquals(1, selectedActions.size());
    }

    @Test public void resumeRailInvokesProvidedDrawerCloseAction() {
        AtomicInteger resumes = new AtomicInteger();
        drawer.setResumeAction(resumes::incrementAndGet);
        select(R.string.ingame_resume);
        assertEquals(1, resumes.get());
    }

    @Test public void taskPagePollsOnlyWhileDrawerPageAndHostAreVisible() {
        drawer.onMenuOpened();
        idle();
        assertEquals(0, activity.handler.requests);
        select(R.string.task_manager);
        idle();
        assertEquals(1, activity.handler.requests);
        assertNotNull(activity.handler.getOnGetProcessInfoListener());
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
        assertEquals(2, activity.handler.requests);
        drawer.onHostStopped();
        int stopped = activity.handler.requests;
        shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS);
        assertEquals(stopped, activity.handler.requests);
        assertNull(activity.handler.getOnGetProcessInfoListener());
        drawer.onHostResumed();
        idle();
        assertEquals(stopped + 1, activity.handler.requests);
        select(R.string.ingame_hud);
        int hidden = activity.handler.requests;
        shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS);
        assertEquals(hidden, activity.handler.requests);
        assertNull(activity.handler.getOnGetProcessInfoListener());
    }

    @Test public void taskPageDoesNotReplaceAnotherProcessInfoConsumer() {
        com.winlator.winhandler.OnGetProcessInfoListener existing = (index, count, info) -> {};
        activity.handler.setOnGetProcessInfoListener(existing);
        select(R.string.task_manager);
        drawer.onMenuOpened();
        idle();
        assertSame(existing, activity.handler.getOnGetProcessInfoListener());
        assertEquals(0, activity.handler.requests);
        drawer.onMenuClosed();
        assertSame(existing, activity.handler.getOnGetProcessInfoListener());
    }

    @Test public void taskListShowsGuestProcessesAndIgnoresCallbacksAfterClosing() {
        select(R.string.task_manager);
        drawer.onMenuOpened();
        idle();
        InGameTaskManagerView task = UiTestSupport.views(drawer, InGameTaskManagerView.class).get(0);
        task.onGetProcessInfo(0, 1, new ProcessInfo(42, "speed2.exe", 16777216, 1, false));
        idle();
        assertNotNull(UiTestSupport.text(drawer, "speed2.exe"));
        assertNotNull(UiTestSupport.text(drawer, activity.getString(R.string.ingame_process_count, 1)));
        drawer.onMenuClosed();
        task.onGetProcessInfo(0, 0, null);
        idle();
        assertNotNull(UiTestSupport.text(drawer, "speed2.exe"));
    }

    @Test public void rendersActualHudSettingsDrawerForVisualReview() throws Exception {
        android.content.SharedPreferences preferences = activity.getSharedPreferences("menu-preview", 0);
        preferences.edit().clear().putBoolean(PerformanceHudView.PREF_ENABLED, true).commit();
        PerformanceHudView hud = new PerformanceHudView(activity, preferences);
        drawer.setHudSettingsView(hud.createSettingsView(null));
        select(R.string.ingame_hud);
        UiTestSupport.exportPreview(drawer, "hud-settings-drawer.png", 410, 591);
        assertTrue(UiTestSupport.described(drawer, activity.getString(R.string.ingame_hud)).isSelected());
        hud.stop();
    }

    @Test public void rendersGuestTaskPageForVisualReview() throws Exception {
        select(R.string.task_manager);
        drawer.onMenuOpened();
        idle();
        InGameTaskManagerView task = UiTestSupport.views(drawer, InGameTaskManagerView.class).get(0);
        task.onGetProcessInfo(0, 1, new ProcessInfo(42, "speed2.exe", 16777216, 1, false));
        idle();
        UiTestSupport.exportPreview(drawer, "task-manager-drawer.png", 410, 591);
        assertNotNull(UiTestSupport.text(drawer, "speed2.exe"));
    }

    private void select(int title) { UiTestSupport.described(drawer, activity.getString(title)).performClick(); }
    private void clickAction(int id) { UiTestSupport.described(drawer, menu.findItem(id).getTitle().toString()).performClick(); }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
}
