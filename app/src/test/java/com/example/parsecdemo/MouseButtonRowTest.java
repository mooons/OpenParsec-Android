package com.example.parsecdemo;

import static org.junit.Assert.*;

import android.app.Activity;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MouseButtonRowTest {
    @Test public void draggingTheMouseHandleDocksItInsteadOfLeavingItInTheDesktop() {
        try (var controller = Robolectric.buildActivity(Activity.class)) {
            controller.get().setTheme(R.style.AppTheme);
            Activity activity = controller.setup().get();
            FrameLayout root = new FrameLayout(activity);
            activity.setContentView(root);
            float[] saved = {-1, -1};
            MouseButtonRow row = new MouseButtonRow(activity, new MouseButtonRow.ButtonListener() {
                @Override public void onButton(int button, boolean pressed) {}
                @Override public void onMiddleClick() {}
                @Override public void onScrollDelta(int x, int y) {}
                @Override public void onRepositioned(float x, float y) { saved[0] = x; saved[1] = y; }
            });
            root.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            layout(root, 800, 360);
            row.setX(300);
            row.setY(100);
            View handle = row.getChildAt(0);
            send(handle, MotionEvent.ACTION_DOWN, 20, 20);
            send(handle, MotionEvent.ACTION_MOVE, 150, 30);
            send(handle, MotionEvent.ACTION_UP, 150, 30);
            assertEquals("Dropping in the right half must dock against the right edge",
                    root.getWidth() - row.getWidth(), row.getX(), 0.5f);
            assertEquals(row.getX(), saved[0], 0);
            handle.performClick();
            layout(root, 800, 360);
            assertEquals("Expanded buttons stay anchored to the right edge",
                    root.getWidth() - row.getWidth(), row.getX(), 0.5f);
            handle.performClick();
            layout(root, 800, 360);
            assertEquals("Collapsing restores the handle to the right edge",
                    root.getWidth() - row.getWidth(), row.getX(), 0.5f);
            send(handle, MotionEvent.ACTION_DOWN, 20, 20);
            send(handle, MotionEvent.ACTION_MOVE, -500, 30);
            send(handle, MotionEvent.ACTION_CANCEL, -500, 30);
            assertEquals("A cancelled drag must still dock on the chosen side", 0, row.getX(), 0.5f);
            assertEquals(row.getX(), saved[0], 0);
        }
    }

    @Test public void compactRowExpandsDragsAndRetainsMouseGestures() {
        try (var controller = Robolectric.buildActivity(Activity.class)) {
            controller.get().setTheme(R.style.AppTheme);
            Activity activity = controller.setup().get();
            FrameLayout root = new FrameLayout(activity);
            activity.setContentView(root);
            List<String> events = new ArrayList<>();
            float[] saved = {-1, -1};
            MouseButtonRow row = new MouseButtonRow(activity, new MouseButtonRow.ButtonListener() {
                @Override public void onButton(int button, boolean pressed) { events.add(button + ":" + pressed); }
                @Override public void onMiddleClick() { events.add("middle"); }
                @Override public void onScrollDelta(int x, int y) { events.add("scroll:" + y); }
                @Override public void onRepositioned(float x, float y) { saved[0] = x; saved[1] = y; }
            });
            root.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            int dp = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            layout(root, dp * 8, dp * 6);
            assertEquals(dp, row.getWidth());
            assertEquals(dp, row.getHeight());
            View handle = row.getChildAt(0);
            assertEquals("Show mouse buttons", handle.getContentDescription());

            row.setX(root.getWidth() - row.getWidth());
            send(handle, MotionEvent.ACTION_DOWN, 20, 20);
            send(handle, MotionEvent.ACTION_MOVE, 21, 21);
            send(handle, MotionEvent.ACTION_UP, 21, 21);
            layout(root, dp * 8, dp * 6);
            assertEquals(dp * 4, row.getWidth());
            assertTrue(row.getX() + row.getWidth() <= root.getWidth());
            assertEquals("Hide mouse buttons", handle.getContentDescription());
            assertEquals(-1, saved[0], 0);

            View left = row.getChildAt(1);
            View middle = row.getChildAt(2);
            View right = row.getChildAt(3);
            send(left, MotionEvent.ACTION_DOWN, 20, 20);
            handle.performClick();
            assertEquals("A held mouse button must remain reachable", View.VISIBLE, left.getVisibility());
            send(left, MotionEvent.ACTION_UP, 20, 20);
            send(right, MotionEvent.ACTION_DOWN, 20, 20);
            send(right, MotionEvent.ACTION_CANCEL, 20, 20);
            send(middle, MotionEvent.ACTION_DOWN, 20, 20);
            send(middle, MotionEvent.ACTION_UP, 20, 20);
            send(middle, MotionEvent.ACTION_DOWN, 20, 20);
            send(middle, MotionEvent.ACTION_MOVE, 20, 44);
            send(middle, MotionEvent.ACTION_UP, 20, 44);
            assertEquals(List.of("1:true", "1:false", "3:true", "3:false", "middle", "scroll:3"), events);

            float expandedX = row.getX();
            handle.performClick();
            layout(root, dp * 8, dp * 6);
            assertEquals(dp, row.getWidth());
            assertEquals("Collapsing must keep the handle in place", expandedX, row.getX(), 0);
            send(handle, MotionEvent.ACTION_DOWN, 20, 20);
            send(handle, MotionEvent.ACTION_MOVE, root.getWidth() * 2, root.getHeight() * 2);
            send(handle, MotionEvent.ACTION_UP, root.getWidth() * 2, root.getHeight() * 2);
            assertEquals(dp, row.getWidth());
            assertEquals(root.getWidth() - dp, saved[0], 0);
            assertEquals(root.getHeight() - dp, saved[1], 0);
        }
    }

    private static void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
        assertTrue(view.dispatchTouchEvent(event));
        event.recycle();
    }
}
