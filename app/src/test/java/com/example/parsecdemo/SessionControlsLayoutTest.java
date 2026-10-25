package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {CursorRenderingTest.ShadowParsec.class, CursorRenderingTest.ShadowSurface.class})
public class SessionControlsLayoutTest {
    @Test public void floatingControlsStayOnTheirChosenEdgeAfterRotation() {
        Intent intent = new Intent()
                .putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session")
                .putExtra(HostListActivity.EXTRA_PEER_ID, "test-peer");
        try (var controller = Robolectric.buildActivity(ParsecActivity.class, intent).setup()) {
            ParsecActivity activity = controller.get();
            FrameLayout root = getField(activity, "root");
            shadowOf(Looper.getMainLooper()).idle();
            layout(root, 360, 800);
            SessionFab fab = getField(activity, "fab");
            fab.resetPosition();
            View button = getField(fab, "fab");
            float edgeGap = root.getWidth() - button.getX() - button.getWidth();
            float heightFraction = button.getY() / (root.getHeight() - button.getHeight());
            MouseButtonRow mouse = getField(activity, "mouseButtonRow");
            Settings settings = getField(activity, "settings");
            settings.mouseRowPosition(root.getWidth() - mouse.getWidth(), 400);
            callInstanceMethod(activity, "reclampMouseRow");
            float mouseHeight = mouse.getY() / (root.getHeight() - mouse.getHeight());

            for (int[] bounds : new int[][]{{800, 360}, {360, 800}, {1000, 400}}) {
                layout(root, bounds[0], bounds[1]);
                assertEquals("Right-docked menu button must follow the edge, not stay in the desktop",
                        root.getWidth() - button.getWidth() - edgeGap, button.getX(), 0.5f);
                assertEquals("Rotation must preserve the relative height",
                        heightFraction * (root.getHeight() - button.getHeight()), button.getY(), 1f);
                assertEquals("The docked mouse handle must also follow the right edge",
                        root.getWidth() - mouse.getWidth(), mouse.getX(), 0.5f);
                assertEquals(mouseHeight * (root.getHeight() - mouse.getHeight()), mouse.getY(), 1f);
            }

            send(button, MotionEvent.ACTION_DOWN, 20, 20);
            send(button, MotionEvent.ACTION_MOVE, -1000, 20);
            send(button, MotionEvent.ACTION_CANCEL, -1000, 20);
            assertEquals(edgeGap, button.getX(), 0.5f);
            callInstanceMethod(activity, "rebuildSessionFab");
            shadowOf(Looper.getMainLooper()).idle();
            layout(root, 360, 800);
            SessionFab refreshed = getField(activity, "fab");
            View refreshedButton = getField(refreshed, "fab");
            assertEquals("Refreshing menu items must preserve the chosen left edge",
                    edgeGap, refreshedButton.getX(), 0.5f);

            callInstanceMethod(activity, "triggerOverlayReset");
            layout(root, 360, 800);
            assertEquals("Reset restores the default bottom-center mouse handle",
                    (root.getWidth() - mouse.getWidth()) / 2f, mouse.getX(), 0.5f);
        }
    }

    @Test public void controlsRemainReachableWhenConfigurationRecoveryPrecedesLayout() {
        Intent intent = new Intent()
                .putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session")
                .putExtra(HostListActivity.EXTRA_PEER_ID, "test-peer");
        try (var controller = Robolectric.buildActivity(ParsecActivity.class, intent).setup()) {
            ParsecActivity activity = controller.get();
            FrameLayout root = getField(activity, "root");
            Settings settings = getField(activity, "settings");
            MouseButtonRow mouse = getField(activity, "mouseButtonRow");
            mouse.getChildAt(0).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            layout(root, 800, 1000);

            for (boolean saved : new boolean[]{true, false}) {
                for (int[] bounds : new int[][]{{1000, 400}, {360, 800}, {800, 400}}) {
                    SessionFab fab = getField(activity, "fab");
                    View button = getField(fab, "fab");
                    button.setX(root.getWidth() - button.getWidth() - 5);
                    button.setY(root.getHeight() - button.getHeight() - 5);
                    mouse.setX(root.getWidth() - mouse.getWidth() - 5);
                    mouse.setY(root.getHeight() - mouse.getHeight() - 5);
                    if (saved) settings.mouseRowPosition(mouse.getX(), mouse.getY());
                    else settings.resetMouseRowPosition();
                    fab.reveal();
                    layout(root, root.getWidth(), root.getHeight());
                    root.getViewTreeObserver().dispatchOnGlobalLayout();

                    Configuration config = new Configuration(activity.getResources().getConfiguration());
                    config.orientation = bounds[0] > bounds[1]
                            ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                    activity.onConfigurationChanged(config);
                    // Run the recovery before delivering the new root size, as can happen on-device.
                    shadowOf(Looper.getMainLooper()).idle();
                    layout(root, bounds[0], bounds[1]);
                    assertInside(root, button);
                    assertInside(root, mouse);
                    assertInside(root, getField(activity, "keyboardButton"));
                    FrameLayout menu = getField(fab, "menu");
                    if (menu != null) assertInside(root, menu);
                }
            }

            // A stale portrait keyboard inset must not push controls above the new viewport.
            callInstanceMethod(activity, "onImeInsetChanged", ClassParameter.from(int.class, 500));
            layout(root, 800, 400);
            assertInside(root, mouse);
            assertInside(root, getField(activity, "keyboardButton"));
            callInstanceMethod(activity, "onImeInsetChanged", ClassParameter.from(int.class, 0));
            layout(root, 800, 400);
            assertInside(root, getField(activity, "keyboardButton"));
        }
    }

    private static void assertInside(View root, View control) {
        assertTrue("Control left/top must remain in the viewport: " + control,
                control.getX() >= 0 && control.getY() >= 0);
        assertTrue("Control right/bottom must remain in the viewport: " + control,
                control.getX() + control.getWidth() <= root.getWidth() + 0.01f
                && control.getY() + control.getHeight() <= root.getHeight() + 0.01f);
    }

    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
        assertTrue(view.dispatchTouchEvent(event));
        event.recycle();
    }

    private static void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }
}
