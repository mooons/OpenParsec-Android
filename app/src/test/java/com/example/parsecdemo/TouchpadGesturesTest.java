package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import parsec.bindings.Parsec;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {CursorRenderingTest.ShadowParsec.class, CursorRenderingTest.ShadowSurface.class})
public class TouchpadGesturesTest {
    private ActivityController<ParsecActivity> controller;
    private ClientGLSurface surface;
    private CursorRenderingTest.ShadowParsec sdk;
    private long downTime;

    @Before public void connect() {
        Intent intent = new Intent()
                .putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session")
                .putExtra(HostListActivity.EXTRA_PEER_ID, "test-peer");
        controller = Robolectric.buildActivity(ParsecActivity.class, intent).setup();
        surface = getField(controller.get(), "surface");
        Parsec client = getField(controller.get(), "parsec");
        client.MOUSE_L = 1; // The native SDK shadow skips the Java constructor.
        sdk = org.robolectric.shadow.api.Shadow.extract(client);
        surface.onClientResize(1600, 1000);
        surface.setSensitivity(1);
        surface.setTrackpadMode(true);
    }

    @After public void disconnect() { controller.close(); }

    @Test public void liftingAfterPrecisePlacementDoesNotFlingTheCursor() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(1100, MotionEvent.ACTION_MOVE, 150, 200);
        touch(1120, MotionEvent.ACTION_MOVE, 151, 200);
        int x = sdk.mouseX, y = sdk.mouseY;
        touch(1121, MotionEvent.ACTION_UP, 153, 201);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals("Lift-off velocity must not move the placed cursor", x, sdk.mouseX);
        assertEquals(y, sdk.mouseY);
        assertFalse(surface.isCursorLocallyControlled());
    }

    @Test @Config(qualifiers = "xhdpi")
    public void secondTapStaysHeldWhileDraggingWithPlatformTimingAndDistance() {
        float slop = ViewConfiguration.get(surface.getContext()).getScaledDoubleTapSlop();
        for (int end : new int[]{MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL}) {
            for (int timing : new int[]{100, ViewConfiguration.getDoubleTapTimeout() - 1}) {
                surface.resetTouchState();
                sdk.buttons.clear();
                touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
                touch(1060, MotionEvent.ACTION_UP, 100, 200);
                long second = 1060 + timing;
                touch(second, MotionEvent.ACTION_DOWN, 100 + slop / 2, 200);
                assertEquals("The second contact must hold left mouse down", List.of(1, -1, 1), sdk.buttons);
                int start = sdk.mouseX;
                touch(second + 600, MotionEvent.ACTION_MOVE, 130 + slop / 2, 200);
                assertEquals(start + 30, sdk.mouseX);
                assertEquals(List.of(1, -1, 1), sdk.buttons);
                touch(second + 650, end, 130 + slop / 2, 200);
                assertEquals("Lift/cancel must release without another click", List.of(1, -1, 1, -1), sdk.buttons);
            }
        }
    }

    @Test public void aStrokeReturningToItsStartMustNotBecomeATap() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(1040, MotionEvent.ACTION_MOVE, 180, 200);
        touch(1080, MotionEvent.ACTION_MOVE, 100, 200);
        touch(1100, MotionEvent.ACTION_UP, 100, 200);
        assertTrue("A completed cursor stroke must not click", sdk.buttons.isEmpty());
    }

    @Test public void stationaryLongPressRightClicksWithoutALeftClickOnLift() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        int x = sdk.mouseX, y = sdk.mouseY;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1));
        assertEquals(List.of(3, -3), sdk.buttons);
        touch(1600, MotionEvent.ACTION_UP, 101, 201);
        assertEquals(List.of(3, -3), sdk.buttons);
        assertEquals(x, sdk.mouseX);
        assertEquals(y, sdk.mouseY);
    }

    @Test public void longPressIsCancelledByMotionMultitouchAndTapDrag() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(1040, MotionEvent.ACTION_MOVE, 180, 200);
        touch(1080, MotionEvent.ACTION_MOVE, 100, 200);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
        touch(1700, MotionEvent.ACTION_UP, 100, 200);
        assertTrue(sdk.buttons.isEmpty());

        touch(2000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(2010, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 100, 200, 150, 200);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
        touch(2700, MotionEvent.ACTION_CANCEL, 100, 200, 150, 200);
        assertTrue(sdk.buttons.isEmpty());

        touch(3000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(3050, MotionEvent.ACTION_UP, 100, 200);
        touch(3150, MotionEvent.ACTION_DOWN, 100, 200);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
        assertEquals(List.of(1, -1, 1), sdk.buttons);
        touch(3800, MotionEvent.ACTION_CANCEL, 100, 200);
        assertEquals(List.of(1, -1, 1, -1), sdk.buttons);
    }

    private void touch(long time, int action, float... xy) {
        if (action == MotionEvent.ACTION_DOWN) downTime = time;
        int count = xy.length / 2;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xy[2 * i];
            coords[i].y = xy[2 * i + 1];
        }
        MotionEvent event = MotionEvent.obtain(downTime, time, action, count, properties, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        assertTrue(surface.onTouchEvent(event));
        event.recycle();
    }
}
