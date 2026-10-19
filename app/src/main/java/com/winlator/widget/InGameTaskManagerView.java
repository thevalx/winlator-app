package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.winhandler.OnGetProcessInfoListener;
import com.winlator.winhandler.ProcessInfo;
import com.winlator.winhandler.WinHandler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/** A lightweight process page, sampled only while this page and its host drawer are visible. */
public class InGameTaskManagerView extends LinearLayout implements OnGetProcessInfoListener {
    private final XServerDisplayActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView cpuValue;
    private final TextView memoryValue;
    private final TextView processCount;
    private final TextView emptyText;
    private final LinearLayout processList;
    private boolean active;
    private int generation;
    private long previousTotal = -1;
    private long previousIdle = -1;
    private Runnable resumeAction;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            updateSystemCards();
            WinHandler winHandler = activity.getWinHandler();
            if (winHandler != null) {
                // WinHandler has one consumer. Do not steal it from an open official dialog.
                OnGetProcessInfoListener listener = winHandler.getOnGetProcessInfoListener();
                if (listener == null) winHandler.setOnGetProcessInfoListener(InGameTaskManagerView.this);
                if (winHandler.getOnGetProcessInfoListener() == InGameTaskManagerView.this) winHandler.listProcesses();
            }
            handler.postDelayed(this, 1000);
        }
    };

    public InGameTaskManagerView(XServerDisplayActivity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);

        LinearLayout cards = new LinearLayout(activity);
        cards.setOrientation(HORIZONTAL);
        cpuValue = text("—", 23, InGameMenuView.TEXT);
        cpuValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cpuValue.setContentDescription(activity.getString(R.string.ingame_cpu_unavailable));
        LinearLayout cpuCard = metricCard(R.string.ingame_cpu, cpuValue);
        LinearLayout.LayoutParams cpuParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.8f);
        cpuParams.rightMargin = dp(10);
        cards.addView(cpuCard, cpuParams);

        memoryValue = text("—", 14, InGameMenuView.TEXT);
        memoryValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout memoryCard = metricCard(R.string.ingame_memory, memoryValue);
        cards.addView(memoryCard, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.2f));
        addView(cards, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        processCount = text(activity.getString(R.string.ingame_process_count, 0), 16, InGameMenuView.TEXT);
        processCount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        processCount.setPadding(0, dp(26), 0, dp(16));
        addView(processCount);

        emptyText = text(activity.getString(R.string.ingame_processes_waiting), 14, InGameMenuView.SECONDARY);
        emptyText.setPadding(0, 0, 0, dp(16));
        addView(emptyText);
        processList = new LinearLayout(activity);
        processList.setOrientation(VERTICAL);
        addView(processList, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView tools = text(activity.getString(R.string.ingame_task_actions), 11, InGameMenuView.SECONDARY);
        tools.setLetterSpacing(0.1f);
        tools.setPadding(0, dp(18), 0, dp(8));
        addView(tools);
        TextView newTask = text(activity.getString(R.string.new_task), 15, InGameMenuView.TEXT);
        newTask.setGravity(Gravity.CENTER);
        newTask.setMinimumHeight(dp(48));
        newTask.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224F98E9), cardBackground(), null));
        newTask.setFocusable(true);
        newTask.setClickable(true);
        newTask.setOnClickListener(view -> ContentDialog.prompt(activity, R.string.new_task, "taskmgr.exe", command -> {
            WinHandler winHandler = activity.getWinHandler();
            if (winHandler != null) winHandler.exec(command);
        }));
        addView(newTask, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setResumeAction(Runnable action) {
        resumeAction = action;
    }

    public void start() {
        if (active) return;
        active = true;
        generation++;
        previousTotal = -1;
        previousIdle = -1;
        handler.post(refresh);
    }

    public void stop() {
        active = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        WinHandler winHandler = activity.getWinHandler();
        if (winHandler != null && winHandler.getOnGetProcessInfoListener() == this) {
            winHandler.setOnGetProcessInfoListener(null);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private LinearLayout metricCard(int titleId, TextView value) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(84));
        card.setPadding(dp(12), dp(12), dp(10), dp(12));
        card.setBackground(cardBackground());
        TextView label = text(activity.getString(titleId), 12, InGameMenuView.SECONDARY);
        card.addView(label);
        LinearLayout.LayoutParams valueParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(8);
        card.addView(value, valueParams);
        return card;
    }

    private void updateSystemCards() {
        Integer usage = readCpuUsage();
        cpuValue.setText(usage != null ? usage + "%" : "—");
        cpuValue.setContentDescription(usage != null ? activity.getString(R.string.ingame_cpu) + " " + usage + "%"
                : activity.getString(R.string.ingame_cpu_unavailable));
        ActivityManager manager = (ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            memoryValue.setText(R.string.ingame_unavailable);
            return;
        }
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        long used = Math.max(0, info.totalMem - info.availMem);
        memoryValue.setText(activity.getString(R.string.ingame_memory_usage,
                StringUtils.formatBytes(used, false), StringUtils.formatBytes(info.totalMem)));
    }

    /** Linux CPU accounting; Android commonly denies access, which is displayed as unavailable. */
    private Integer readCpuUsage() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;
            String[] values = line.trim().split("\\s+");
            if (values.length < 5) return null;
            long total = 0;
            // guest/guest_nice are already included in user/nice; avoid counting them twice.
            for (int i = 1; i < values.length && i <= 8; i++) total += Long.parseLong(values[i]);
            long idle = Long.parseLong(values[4]);
            if (values.length > 5) idle += Long.parseLong(values[5]);
            long totalDelta = total - previousTotal;
            long idleDelta = idle - previousIdle;
            boolean hasSample = previousTotal >= 0 && previousIdle >= 0;
            previousTotal = total;
            previousIdle = idle;
            if (!hasSample || totalDelta <= 0 || idleDelta < 0) return null;
            return (int)Math.max(0, Math.min(100, Math.round(100.0 * (totalDelta - idleDelta) / totalDelta)));
        }
        catch (IOException | NumberFormatException | SecurityException ignored) {
            previousTotal = -1;
            previousIdle = -1;
            return null;
        }
    }

    @Override
    public void onGetProcessInfo(int index, int count, ProcessInfo processInfo) {
        final int callbackGeneration = generation;
        handler.post(() -> {
            if (!active || callbackGeneration != generation) return;
            processCount.setText(activity.getString(R.string.ingame_process_count, count));
            emptyText.setVisibility(count == 0 ? VISIBLE : GONE);
            if (count == 0) {
                emptyText.setText(R.string.ingame_no_processes);
                processList.removeAllViews();
                return;
            }
            if (processInfo == null || index < 0 || index >= count) return;
            ProcessRow row;
            if (index < processList.getChildCount()) row = (ProcessRow)processList.getChildAt(index);
            else {
                row = new ProcessRow();
                LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(10);
                processList.addView(row, params);
            }
            row.bind(processInfo);
            if (index == count - 1) {
                while (processList.getChildCount() > count) processList.removeViewAt(processList.getChildCount() - 1);
            }
        });
    }

    private void showProcessActions(View anchor, ProcessInfo process) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popup.setForceShowIcon(true);
        popup.inflate(R.menu.process_popup_menu);
        popup.setOnMenuItemClickListener(item -> {
            WinHandler winHandler = activity.getWinHandler();
            if (winHandler == null) return false;
            switch (item.getItemId()) {
                case R.id.menu_item_process_affinity:
                    ContentDialog dialog = new ContentDialog(activity, R.layout.cpu_list_dialog);
                    dialog.setTitle(process.name);
                    dialog.setIcon(R.drawable.icon_cpu);
                    CPUListView cpuList = dialog.findViewById(R.id.CPUListView);
                    cpuList.setCheckedCPUList(process.getCPUList());
                    dialog.setOnConfirmCallback(() -> winHandler.setProcessAffinity(process.pid,
                            ProcessHelper.getAffinityMask(cpuList.getCheckedCPUList())));
                    dialog.show();
                    return true;
                case R.id.menu_item_bring_to_front:
                    winHandler.bringToFront(process.name);
                    if (resumeAction != null) resumeAction.run();
                    return true;
                case R.id.menu_item_end_process:
                    ContentDialog.confirm(activity, R.string.do_you_want_to_end_this_process,
                            () -> winHandler.killProcess(null, process.pid));
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private class ProcessRow extends LinearLayout {
        private final TextView name;
        private final TextView pid;
        private final TextView memory;
        private final TextView actions;
        private ProcessInfo process;

        ProcessRow() {
            super(activity);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(8), dp(2), dp(8));
            setMinimumHeight(dp(78));
            setBackground(cardBackground());
            ImageView icon = new ImageView(activity);
            icon.setImageResource(R.drawable.taskmgr_process);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LayoutParams(dp(27), dp(27)));

            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(VERTICAL);
            name = text("", 15, InGameMenuView.TEXT);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);
            pid = text("", 12, InGameMenuView.SECONDARY);
            LayoutParams pidParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            pidParams.topMargin = dp(4);
            labels.addView(pid, pidParams);
            LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            labelParams.leftMargin = dp(10);
            addView(labels, labelParams);

            memory = text("", 12, InGameMenuView.SECONDARY);
            memory.setGravity(Gravity.END);
            memory.setMaxLines(2);
            LayoutParams memoryParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            memoryParams.leftMargin = dp(6);
            addView(memory, memoryParams);

            actions = text("⋮", 25, InGameMenuView.SECONDARY);
            actions.setGravity(Gravity.CENTER);
            actions.setFocusable(true);
            actions.setClickable(true);
            actions.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3378ADEE), null, null));
            actions.setOnClickListener(view -> {
                if (process != null) showProcessActions(view, process);
            });
            addView(actions, new LayoutParams(dp(48), dp(48)));
        }

        void bind(ProcessInfo process) {
            this.process = process;
            name.setText(process.name + (process.wow64Process ? " *32" : ""));
            pid.setText(activity.getString(R.string.ingame_process_pid, process.pid));
            memory.setText(process.getFormattedMemoryUsage());
            actions.setContentDescription(activity.getString(R.string.ingame_process_actions, process.name));
        }
    }

    private TextView text(CharSequence value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(InGameMenuView.CARD);
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), InGameMenuView.ACCENT);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
