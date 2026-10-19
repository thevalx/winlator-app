package com.winlator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.core.AppUtils;
import com.winlator.core.LocaleHelper;
import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.ExternalControllerBinding;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.inputcontrols.TriggerMode;
import com.winlator.math.Mathf;

public class ExternalControllerBindingsActivity extends AppCompatActivity {
    private TextView emptyTextView;
    private ControlsProfile profile;
    private ExternalController controller;
    private RecyclerView recyclerView;
    private ControllerBindingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.external_controller_bindings_activity);

        Intent intent = getIntent();
        int profileId = intent.getIntExtra("profile_id", 0);
        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, profileId));
        String controllerId = intent.getStringExtra("controller_id");

        controller = profile.getController(controllerId);
        if (controller == null) {
            controller = profile.addController(controllerId);
            profile.save();
        }

        Toolbar toolbar = findViewById(R.id.Toolbar);
        toolbar.setTitle(controller.getName());
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);

        emptyTextView = findViewById(R.id.TVEmptyText);
        recyclerView = findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.list_item_divider));
        recyclerView.addItemDecoration(itemDecoration);
        recyclerView.setAdapter(adapter = new ControllerBindingsAdapter());
        updateEmptyTextView();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    private void updateControllerBinding(int keyCode, Binding binding) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return;

        ExternalControllerBinding controllerBinding = controller.getControllerBinding(keyCode);
        int position;
        if (controllerBinding == null) {
            controllerBinding = new ExternalControllerBinding();
            controllerBinding.setKeyCode(keyCode);
            controllerBinding.setBinding(binding);

            controller.addControllerBinding(controllerBinding);
            profile.save();
            adapter.notifyDataSetChanged();
            updateEmptyTextView();
            position = controller.getPosition(controllerBinding);
        }
        else animateItemView(position = controller.getPosition(controllerBinding));
        recyclerView.scrollToPosition(position);
    }

    private void processJoystickInput() {
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        Binding binding = Binding.NONE;
        final int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
        GamepadState state = controller.getGamepadState();
        final float[] values = {state.thumbLX, state.thumbLY, state.thumbRX, state.thumbRY, state.getDPadX(), state.getDPadY()};

        byte sign;
        for (int i = 0; i < axes.length; i++) {
            if ((sign = Mathf.sign(values[i])) != 0) {
                if (axes[i] == MotionEvent.AXIS_X || axes[i] == MotionEvent.AXIS_Z) {
                    binding = sign > 0 ? Binding.MOUSE_MOVE_RIGHT : Binding.MOUSE_MOVE_LEFT;
                }
                else if (axes[i] == MotionEvent.AXIS_Y || axes[i] == MotionEvent.AXIS_RZ) {
                    binding = sign > 0 ? Binding.MOUSE_MOVE_DOWN : Binding.MOUSE_MOVE_UP;
                }
                else if (axes[i] == MotionEvent.AXIS_HAT_X) {
                    binding = sign > 0 ? Binding.KEY_D : Binding.KEY_A;
                }
                else if (axes[i] == MotionEvent.AXIS_HAT_Y) {
                    binding = sign > 0 ? Binding.KEY_S : Binding.KEY_W;
                }

                keyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], sign);
                break;
            }
        }

        updateControllerBinding(keyCode, binding);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // The binding editor detects trigger presses even on controllers without L2/R2 key events.
        controller.setTriggerType(TriggerMode.AS_BUTTON);
        if (event.getDeviceId() == controller.getDeviceId() && controller.updateStateFromMotionEvent(event)) {
            GamepadState state = controller.getGamepadState();
            if (state.isPressed(ExternalController.IDX_BUTTON_L2)) updateControllerBinding(KeyEvent.KEYCODE_BUTTON_L2, Binding.NONE);
            if (state.isPressed(ExternalController.IDX_BUTTON_R2)) updateControllerBinding(KeyEvent.KEYCODE_BUTTON_R2, Binding.NONE);
            processJoystickInput();
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getDeviceId() == controller.getDeviceId() && event.getRepeatCount() == 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) updateControllerBinding(event.getKeyCode(), Binding.NONE);
            return true;
        }
        else return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        finish();
        return true;
    }

    private class ControllerBindingsAdapter extends RecyclerView.Adapter<ControllerBindingsAdapter.ViewHolder> {
        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton removeButton;
            private final TextView title;
            private final Spinner bindingType;
            private final Spinner binding;

            private ViewHolder(View view) {
                super(view);
                this.title = view.findViewById(R.id.TVTitle);
                this.bindingType = view.findViewById(R.id.SBindingType);
                this.binding = view.findViewById(R.id.SBinding);
                this.removeButton = view.findViewById(R.id.BTRemove);
            }
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.external_controller_binding_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final ExternalControllerBinding item = controller.getControllerBindingAt(position);
            holder.title.setText(item.toString());
            loadBindingSpinner(holder, item);
            holder.removeButton.setOnClickListener((view) -> {
                controller.removeControllerBinding(item);
                profile.save();
                notifyDataSetChanged();
                updateEmptyTextView();
            });
        }

        @Override
        public final int getItemCount() {
            return controller.getControllerBindingCount();
        }

        private void loadBindingSpinner(ViewHolder holder, final ExternalControllerBinding item) {
            holder.bindingType.setOnItemSelectedListener(null);
            holder.binding.setOnItemSelectedListener(null);

            Binding selectedBinding = item.getBinding();
            final int[] selectedCategory = {selectedBinding.isWinlator() ? 3 :
                selectedBinding.isGamepad() ? 2 : selectedBinding.isMouse() ? 1 : 0};
            holder.bindingType.setSelection(selectedCategory[0], false);
            loadBindingActions(holder, item, selectedCategory[0], false);

            holder.bindingType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position != holder.bindingType.getSelectedItemPosition() || position == selectedCategory[0]) return;
                    selectedCategory[0] = position;
                    loadBindingActions(holder, item, position, true);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        private void loadBindingActions(ViewHolder holder, ExternalControllerBinding item,
                                        int category, boolean categoryChanged) {
            String[] labels;
            final Binding[] values;
            switch (category) {
                case 1:
                    labels = Binding.mouseBindingLabels();
                    values = Binding.mouseBindingValues();
                    break;
                case 2:
                    labels = Binding.gamepadBindingLabels();
                    values = Binding.gamepadBindingValues();
                    break;
                case 3:
                    labels = Binding.winlatorBindingLabels();
                    values = Binding.winlatorBindingValues();
                    break;
                default:
                    labels = Binding.keyboardBindingLabels();
                    values = Binding.keyboardBindingValues();
                    break;
            }
            holder.binding.setOnItemSelectedListener(null);
            final ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(
                ExternalControllerBindingsActivity.this, android.R.layout.simple_spinner_dropdown_item, labels);
            holder.binding.setAdapter(actionAdapter);
            int selected = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == item.getBinding()) { selected = i; break; }
            }
            holder.binding.setSelection(selected, false);
            // Rendering an existing row must never rewrite its saved mapping.
            if (categoryChanged && values[selected] != item.getBinding()) {
                item.setBinding(values[selected]);
                profile.save();
            }
            holder.binding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (parent.getAdapter() != actionAdapter ||
                        holder.bindingType.getSelectedItemPosition() != category ||
                        position < 0 || position >= values.length) return;
                    Binding binding = values[position];
                    if (binding != item.getBinding()) {
                        item.setBinding(binding);
                        profile.save();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void updateEmptyTextView() {
        emptyTextView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void animateItemView(int position) {
        final ControllerBindingsAdapter.ViewHolder holder = (ControllerBindingsAdapter.ViewHolder)recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            final int color = AppUtils.getThemeColor(this, R.attr.colorAccent);
            final ValueAnimator animator = ValueAnimator.ofFloat(0.4f, 0.0f);
            animator.setDuration(200);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener((animation) -> {
                float alpha = (float)animation.getAnimatedValue();
                holder.itemView.setBackgroundColor(Color.argb((int)(alpha * 255), Color.red(color), Color.green(color), Color.blue(color)));
            });
            animator.start();
        }
    }
}
