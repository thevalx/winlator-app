package com.winlator.widget;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.navigation.NavigationView;
import com.winlator.R;
import com.winlator.XServerDisplayActivity;

/** The in-session drawer. Actions continue through the official activity menu handlers. */
public class InGameMenuView extends LinearLayout {
    public static final int BACKGROUND = Color.rgb(6, 11, 19);
    public static final int CARD = Color.rgb(10, 18, 32);
    public static final int TEXT = Color.rgb(239, 244, 251);
    public static final int SECONDARY = Color.rgb(158, 170, 188);
    public static final int ACCENT = Color.rgb(141, 188, 250);

    private static final int TAB_DESKTOP = 0;
    private static final int TAB_SETTINGS = 1;
    private static final int TAB_INPUT = 2;
    private static final int TAB_HUD = 3;
    private static final int TAB_TASKS = 4;
    private static final int TAB_RESUME = 5;

    private final XServerDisplayActivity activity;
    private final Menu menu;
    private final NavigationView.OnNavigationItemSelectedListener listener;
    private final RailIcon[] tabButtons = new RailIcon[6];
    private final TextView title;
    private final ScrollView pageScroll;
    private final LinearLayout page;
    private final InGameTaskManagerView taskManager;
    private final FrameLayout hudSettings = new FrameLayout(getContext());
    private int selectedTab = TAB_DESKTOP;
    private boolean menuOpen;
    private boolean hostResumed = true;
    private Runnable resumeAction;

    public InGameMenuView(XServerDisplayActivity activity, Menu menu,
                          NavigationView.OnNavigationItemSelectedListener listener) {
        super(activity);
        this.activity = activity;
        this.menu = menu;
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setBackgroundColor(BACKGROUND);
        setPadding(dp(8), dp(10), dp(16), dp(10));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        ScrollView railScroll = new ScrollView(activity);
        railScroll.setFillViewport(true);
        railScroll.setVerticalScrollBarEnabled(false);
        railScroll.setBackground(rounded(CARD, 32, ACCENT));
        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(5), dp(8), dp(5), dp(8));
        railScroll.addView(rail, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(railScroll, new LayoutParams(dp(68), LayoutParams.MATCH_PARENT));

        int[] tabTitles = {R.string.ingame_desktop, R.string.ingame_settings, R.string.ingame_input,
                R.string.ingame_hud, R.string.task_manager, R.string.ingame_resume};
        for (int i = 0; i < tabButtons.length; i++) {
            final int tab = i;
            RailIcon button = new RailIcon(activity, i);
            button.setContentDescription(activity.getString(tabTitles[i]));
            button.setTooltipText(activity.getString(tabTitles[i]));
            button.setFocusable(true);
            button.setClickable(true);
            button.setOnClickListener(view -> {
                if (tab == TAB_RESUME) {
                    if (resumeAction != null) resumeAction.run();
                }
                else selectTab(tab);
            });
            tabButtons[i] = button;
            LinearLayout.LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(54));
            if (i != 0) params.topMargin = dp(6);
            rail.addView(button, params);
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(VERTICAL);
        LinearLayout.LayoutParams contentParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        contentParams.leftMargin = dp(20);
        addView(content, contentParams);

        title = text(activity.getString(R.string.ingame_desktop), 25, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(16));
        ViewCompat.setAccessibilityHeading(title, true);
        content.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        pageScroll = new ScrollView(activity);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(activity);
        page.setOrientation(VERTICAL);
        page.setPadding(0, 0, 0, dp(12));
        pageScroll.addView(page, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        content.addView(pageScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        taskManager = new InGameTaskManagerView(activity);
        taskManager.setResumeAction(() -> {
            if (resumeAction != null) resumeAction.run();
        });
        selectTab(TAB_DESKTOP);
    }

    public void setHudSettingsView(View view) {
        replaceContent(hudSettings, view);
    }

    private void replaceContent(FrameLayout container, View view) {
        container.removeAllViews();
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
            container.addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    public void setResumeAction(Runnable action) {
        resumeAction = action;
    }

    public void onMenuOpened() {
        menuOpen = true;
        updateTaskVisibility();
    }

    public void onMenuClosed() {
        menuOpen = false;
        updateTaskVisibility();
    }

    public void onHostStopped() {
        hostResumed = false;
        updateTaskVisibility();
    }

    public void onHostResumed() {
        hostResumed = true;
        updateTaskVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        taskManager.stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        return tabButtons[selectedTab].requestFocus(direction, previouslyFocusedRect);
    }

    private void updateTaskVisibility() {
        if (menuOpen && hostResumed && selectedTab == TAB_TASKS) taskManager.start();
        else taskManager.stop();
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        for (int i = 0; i < tabButtons.length; i++) tabButtons[i].setSelected(i == tab);
        page.removeAllViews();
        switch (tab) {
            case TAB_DESKTOP:
                title.setText(R.string.ingame_desktop);
                addSection(R.string.ingame_session);
                addAction(R.id.menu_item_keyboard);
                addAction(R.id.menu_item_active_windows);
                addSection(R.string.ingame_display);
                addAction(R.id.menu_item_toggle_fullscreen);
                addAction(R.id.menu_item_magnifier);
                break;
            case TAB_SETTINGS:
                title.setText(R.string.ingame_settings);
                addSection(R.string.ingame_display);
                addAction(R.id.menu_item_screen_effect);
                addAction(R.id.menu_item_pip_mode);
                addSection(R.string.ingame_tools);
                addAction(R.id.menu_item_logs);
                addAction(R.id.menu_item_touchpad_help);
                addAction(R.id.menu_item_exit);
                break;
            case TAB_INPUT:
                title.setText(R.string.input_controls);
                addAction(R.id.menu_item_input_controls);
                addSection(R.string.ingame_controller);
                TextView bindingHelp = text(activity.getString(R.string.ingame_controller_help), 14, SECONDARY);
                bindingHelp.setLineSpacing(dp(4), 1);
                bindingHelp.setPadding(0, dp(4), 0, dp(12));
                page.addView(bindingHelp, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                break;
            case TAB_HUD:
                title.setText(R.string.ingame_hud);
                addEmbedded(hudSettings);
                break;
            case TAB_TASKS:
                title.setText(R.string.task_manager);
                addEmbedded(taskManager);
                break;
        }
        pageScroll.scrollTo(0, 0);
        updateTaskVisibility();
    }

    private void addEmbedded(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
        page.addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addSection(int textId) {
        TextView label = text(activity.getString(textId), 11, SECONDARY);
        label.setLetterSpacing(0.1f);
        label.setPadding(0, dp(12), 0, dp(10));
        page.addView(label);
    }

    private void addAction(int id) {
        MenuItem item = menu.findItem(id);
        if (item == null || !item.isVisible()) return;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(10), dp(8));
        row.setMinimumHeight(dp(56));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224F98E9), rounded(CARD, 17, 0), null));
        row.setFocusable(true);
        row.setClickable(true);
        row.setEnabled(item.isEnabled());
        row.setContentDescription(item.getTitle());
        row.setOnClickListener(view -> listener.onNavigationItemSelected(item));
        if (item.getIcon() != null) {
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(item.getIcon().mutate());
            icon.setImageTintList(ColorStateList.valueOf(ACCENT));
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LayoutParams(dp(22), dp(22)));
        }
        TextView label = text(item.getTitle(), 15, TEXT);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        labelParams.leftMargin = dp(12);
        row.addView(label, labelParams);
        TextView chevron = text("›", 24, SECONDARY);
        chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        page.addView(row, params);
    }

    private TextView text(CharSequence value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Small vector-like icons keep the rail crisp at every screen density. */
    private class RailIcon extends View {
        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        RailIcon(XServerDisplayActivity activity, int type) {
            super(activity);
            this.type = type;
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            setBackground(new RippleDrawable(ColorStateList.valueOf(0x3378ADEE),
                    rounded(selected ? Color.rgb(25, 59, 98) : Color.TRANSPARENT, 22, 0), null));
            invalidate();
        }

        @Override
        protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate((getWidth() - dp(26)) / 2f, (getHeight() - dp(26)) / 2f);
            canvas.scale(dp(26) / 26f, dp(26) / 26f);
            paint.setColor(isSelected() || isFocused() ? ACCENT : Color.rgb(122, 137, 157));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch (type) {
                case TAB_DESKTOP:
                    canvas.drawRect(2, 3, 24, 18, paint);
                    canvas.drawLine(13, 18, 13, 23, paint);
                    canvas.drawLine(8, 23, 18, 23, paint);
                    break;
                case TAB_SETTINGS:
                    canvas.drawCircle(13, 13, 7, paint);
                    canvas.drawCircle(13, 13, 3, paint);
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4;
                        canvas.drawLine(13 + (float)Math.cos(angle)*8, 13 + (float)Math.sin(angle)*8,
                                13 + (float)Math.cos(angle)*11, 13 + (float)Math.sin(angle)*11, paint);
                    }
                    break;
                case TAB_INPUT:
                    rect.set(4, 8, 22, 22);
                    canvas.drawRoundRect(rect, 5, 5, paint);
                    canvas.drawLine(8, 13, 8, 18, paint);
                    canvas.drawLine(5.5f, 15.5f, 10.5f, 15.5f, paint);
                    canvas.drawCircle(17, 14, 1, paint);
                    canvas.drawCircle(19, 18, 1, paint);
                    path.moveTo(10, 8);
                    path.lineTo(10, 5);
                    path.quadTo(10, 2, 14, 3);
                    path.lineTo(17, 4);
                    canvas.drawPath(path, paint);
                    break;
                case TAB_HUD:
                    rect.set(2, 4, 24, 26);
                    canvas.drawArc(rect, 190, 160, false, paint);
                    canvas.drawLine(6, 23, 20, 23, paint);
                    canvas.drawLine(13, 16, 20, 9, paint);
                    break;
                case TAB_TASKS:
                    canvas.drawRect(2, 4, 24, 22, paint);
                    path.moveTo(4, 15);
                    path.lineTo(8, 15);
                    path.lineTo(11, 8);
                    path.lineTo(15, 19);
                    path.lineTo(18, 12);
                    path.lineTo(22, 12);
                    canvas.drawPath(path, paint);
                    break;
                case TAB_RESUME:
                    canvas.drawCircle(13, 13, 11, paint);
                    paint.setStyle(Paint.Style.FILL);
                    path.moveTo(10, 7);
                    path.lineTo(19, 13);
                    path.lineTo(10, 19);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
            }
            canvas.restore();
        }
    }
}
