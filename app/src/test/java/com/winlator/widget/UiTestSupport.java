package com.winlator.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

final class UiTestSupport {
    private UiTestSupport() {}

    static <T extends View> List<T> views(View root, Class<T> type) {
        ArrayList<T> result = new ArrayList<>();
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup)root;
            for (int i = 0; i < parent.getChildCount(); i++) result.addAll(views(parent.getChildAt(i), type));
        }
        return result;
    }

    static View described(View root, String label) {
        for (View view : views(root, View.class)) {
            if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        }
        throw new AssertionError("Missing accessible control: " + label);
    }

    static TextView text(View root, String label) {
        for (TextView view : views(root, TextView.class)) if (label.contentEquals(view.getText())) return view;
        throw new AssertionError("Missing text: " + label);
    }

    static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    /** Optional native-Android render for visual review, using deterministic test fixtures. */
    static void exportPreview(View view, String name, int width, int height) throws Exception {
        layout(view, width, height);
        String destination = System.getProperty("winlator.preview.dir", "");
        if (destination.isEmpty()) return;
        File directory = new File(destination);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        for (View child : views(view, View.class)) child.jumpDrawablesToCurrentState();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }
}
