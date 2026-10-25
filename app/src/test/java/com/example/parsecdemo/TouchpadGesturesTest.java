package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

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
        sdk = org.robolectric.shadow.api.Shadow.extract(getField(controller.get(), "parsec"));
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
