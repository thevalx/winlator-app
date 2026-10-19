package com.winlator.widget;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.winlator.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS-w1280dp-h591dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PerformanceHudViewTest {
    private Activity activity;
    private SharedPreferences preferences;
    private PerformanceHudView hud;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.AppThemeFullscreenDark);
        preferences = activity.getSharedPreferences("hud-ui-test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        hud = new PerformanceHudView(activity, preferences);
    }

    @After public void tearDown() {
        if (hud != null) hud.stop();
        activity.finish();
    }

    @Test public void containerHudSettingIsInheritedUntilUserMakesAChoice() {
        assertFalse(hud.isEnabled(false));
        assertTrue(hud.isEnabled(true));
        assertFalse(preferences.contains(PerformanceHudView.PREF_ENABLED));
        hud.refreshSettings();
        assertEquals(View.VISIBLE, hud.getVisibility());
        assertTrue(hud.isModernStyle());
    }

    @Test public void enableSwitchPersistsExplicitChoiceAndNotifiesHost() {
        AtomicInteger callbacks = new AtomicInteger();
        View settings = hud.createSettingsView(callbacks::incrementAndGet);
        Switch enable = UiTestSupport.views(settings, Switch.class).get(0);
        assertFalse(enable.isChecked());
        enable.performClick();
        assertTrue(preferences.getBoolean(PerformanceHudView.PREF_ENABLED, false));
        assertEquals(View.VISIBLE, hud.getVisibility());
        assertEquals(1, callbacks.get());
        PerformanceHudView recreated = new PerformanceHudView(activity, preferences);
        assertTrue(recreated.isEnabled(false));
        enable.performClick();
        assertFalse(preferences.getBoolean(PerformanceHudView.PREF_ENABLED, true));
        assertEquals(View.GONE, hud.getVisibility());
        assertEquals(2, callbacks.get());
        recreated.stop();
    }

    @Test public void styleDropdownSwitchesToClassicAndKeepsPreferenceAfterRecreation() {
        preferences.edit().putBoolean(PerformanceHudView.PREF_ENABLED, true).commit();
        hud.refreshSettings();
        AtomicInteger callbacks = new AtomicInteger();
        View settings = hud.createSettingsView(callbacks::incrementAndGet);
        Spinner style = UiTestSupport.views(settings, Spinner.class).get(0);
        UiTestSupport.layout(settings, 340, 700);
        shadowOf(Looper.getMainLooper()).idle();
        style.setSelection(1);
        UiTestSupport.layout(settings, 340, 700);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("classic", preferences.getString(PerformanceHudView.PREF_STYLE, ""));
        assertFalse(hud.isModernStyle());
        assertEquals(View.GONE, hud.getVisibility());
        assertEquals(1, callbacks.get());
        PerformanceHudView recreated = new PerformanceHudView(activity, preferences);
        assertFalse(recreated.isModernStyle());
        recreated.stop();
        style.setSelection(0);
        UiTestSupport.layout(settings, 340, 700);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(hud.isModernStyle());
        assertEquals(View.VISIBLE, hud.getVisibility());
    }

    @Test public void metricCheckboxChangesRenderedStripAndPersists() {
        hud.setFps(59.8f);
        View settings = hud.createSettingsView(null);
        CheckBox fps = (CheckBox)UiTestSupport.text(settings, activity.getString(R.string.hud_fps));
        assertTrue(fps.isChecked());
        assertTrue(strip().contains("FPS 60"));
        fps.performClick();
        assertFalse(preferences.getBoolean("hud_show_fps", true));
        assertFalse(strip().contains("FPS"));
        PerformanceHudView recreated = new PerformanceHudView(activity, preferences);
        CheckBox persisted = (CheckBox)UiTestSupport.text(recreated.createSettingsView(null), activity.getString(R.string.hud_fps));
        assertFalse(persisted.isChecked());
        recreated.stop();
    }

    @Test public void unavailableSensorsDoNotPretendToBeZero() {
        String value = strip();
        assertTrue(value.contains("GPU " + activity.getString(R.string.hud_unavailable)));
        assertTrue(value.contains("FPS " + activity.getString(R.string.hud_unavailable)));
        assertFalse(value.contains("0%"));
    }

    @Test public void rendererOptionDisplaysCurrentRendererWithoutControlCharacters() {
        hud.setRendererInfo(" Turnip\nVulkan ");
        View settings = hud.createSettingsView(null);
        CheckBox renderer = (CheckBox)UiTestSupport.text(settings, activity.getString(R.string.hud_renderer));
        assertFalse(renderer.isChecked());
        renderer.performClick();
        assertTrue(strip().contains("RENDERER Turnip Vulkan"));
        assertFalse(strip().contains("\n"));
    }

    @Test public void fullMetricStripFitsNarrowLandscapeWidthAndRenders() throws Exception {
        preferences.edit().putBoolean(PerformanceHudView.PREF_ENABLED, true)
                .putBoolean("hud_show_renderer", true).putBoolean("hud_show_gpu_name", true).commit();
        hud.refreshSettings();
        hud.setFps(60);
        hud.setRendererInfo("Turnip / Vulkan");
        UiTestSupport.exportPreview(hud, "modern-hud.png", 780, 32);
        TextView strip = UiTestSupport.views(hud, TextView.class).get(0);
        assertTrue(strip.getWidth() > 0);
        assertTrue(strip.getWidth() <= hud.getWidth());
        assertEquals(1, strip.getLineCount());
        assertTrue(strip.getTextSize() >= 7f);
    }

    private String strip() { return UiTestSupport.views(hud, TextView.class).get(0).getText().toString(); }
}
