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

    @Test @Config(qualifiers = "420dpi")
    public void capturedPixelLiftTailsDoNotDisplaceThePlacedCursor() {
        // Anonymized Pixel 7 Pro samples: ms since the last settled MOVE, x/y offsets.
        // Each final row is UP. Pressure stayed nearly constant in several captures.
        double[][][] tails = {
                {{549, .474, -.75}, {557, 1.875, -.75}, {571, 1.5, -.75}},
                {{232, 1.249, -.499}, {240, 3.211, -1.74}, {256, 10.5, -2.25}, {260, 10.5, -2.25}},
                {{401, 9.75, 1.5}, {405, 9.75, 1.5}},
                {{146, 4.732, 0}, {156, 9.75, -1.5}, {160, 9.75, -1.5}},
                {{96, .75, 0}, {113, .75, 0}},
                {{162, 1.913, 0}, {164, 2.25, 0}, {176, 2.25, 0}},
                {{357, 0, -.75}, {413, .961, 0}, {420, 2.25, 1.5}, {428, 2.25, 1.5}},
                {{85, 1.601, 0}, {93, 2.312, -.75}, {99, 3.375, -.75}, {121, 3, -.75}},
                {{367, 1.6162, -.4912}, {375, 2.5781, -.75}, {384, 4.0869, -.75},
                        {384, 4.125, -.75}, {396, 4.125, -.75}}
        };
        for (int n = 0; n < tails.length; n++) {
            surface.resetTouchState();
            sdk.buttons.clear();
            touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
            waitMs(40);
            touch(1040, MotionEvent.ACTION_MOVE, 150, 200);
            int previous = 0;
            for (int i = 0; i < tails[n].length; i++) {
                double[] sample = tails[n][i];
                int elapsed = (int) sample[0];
                waitMs(elapsed - previous);
                touch(1040 + elapsed, i == tails[n].length - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                        150 + (float) sample[1], 200 + (float) sample[2]);
                previous = elapsed;
            }
            waitMs(60);
            assertEquals("Captured tail " + (n + 1) + " must not move x on lift", 850, sdk.mouseX);
            // Capture 7 includes an earlier correction held long enough to be displayed.
            assertEquals("Captured tail " + (n + 1) + " must keep the displayed y", n == 6 ? 499 : 500, sdk.mouseY);
            assertTrue("Protection must not create a click", sdk.buttons.isEmpty());
        }
    }

    @Test public void continuousSinglePixelCorrectionsRemainImmediate() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        for (int i = 1; i <= 20; i++) {
            touch(1000 + 8 * i, MotionEvent.ACTION_MOVE, 100 + i, 200);
            assertEquals(800 + i, sdk.mouseX);
        }
        for (int i = 1; i <= 20; i++) {
            touch(1160 + 8 * i, MotionEvent.ACTION_MOVE, 120 - i, 200);
            assertEquals(820 - i, sdk.mouseX);
        }
        touch(1330, MotionEvent.ACTION_UP, 100, 200);
    }

    @Test public void tinyCorrectionsAfterAPauseAreCommittedIfContactContinues() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(1040, MotionEvent.ACTION_MOVE, 150, 200);
        touch(1140, MotionEvent.ACTION_MOVE, 151, 200);
        assertEquals(850, sdk.mouseX);
        waitMs(39);
        assertEquals(850, sdk.mouseX);
        waitMs(2);
        assertEquals("An intentional one-pixel correction must not be swallowed", 851, sdk.mouseX);
        touch(1182, MotionEvent.ACTION_MOVE, 150, 200);
        waitMs(41);
        assertEquals("A one-pixel reversal must also be preserved", 850, sdk.mouseX);
        touch(1230, MotionEvent.ACTION_UP, 150, 200);
    }

    @Test public void largerResumedMovementFlushesTheWholePendingDeltaImmediately() {
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(1040, MotionEvent.ACTION_MOVE, 150, 200);
        touch(1140, MotionEvent.ACTION_MOVE, 151, 200);
        touch(1150, MotionEvent.ACTION_MOVE, 180, 200);
        assertEquals(880, sdk.mouseX);
        waitMs(60);
        assertEquals("The pending delta must not be applied twice", 880, sdk.mouseX);
        touch(1220, MotionEvent.ACTION_UP, 180, 200);
    }

    @Test public void heldButtonsAndPointerLockDoNotWaitForProtection() {
        for (boolean relative : new boolean[]{false, true}) {
            surface.resetTouchState();
            surface.setRelativeMouseMode(relative);
            surface.setExternalButtonHeld(!relative);
            touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
            touch(1040, MotionEvent.ACTION_MOVE, 150, 200);
            touch(1140, MotionEvent.ACTION_MOVE, 151, 200);
            assertEquals(relative, sdk.motionRelative);
            assertEquals(relative ? 1 : 851, sdk.motionX);
            touch(1150, MotionEvent.ACTION_UP, 151, 200);
        }
    }

    @Test public void cancelledOrResetGesturesCannotSendDelayedMotion() {
        for (int action = 0; action < 3; action++) {
            surface.setRelativeMouseMode(false);
            surface.resetTouchState();
            touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
            touch(1040, MotionEvent.ACTION_MOVE, 150, 200);
            touch(1140, MotionEvent.ACTION_MOVE, 151, 200);
            if (action == 0) touch(1141, MotionEvent.ACTION_CANCEL, 151, 200);
            else if (action == 1) surface.resetTouchState();
            else surface.setRelativeMouseMode(true);
            int x = sdk.motionX, y = sdk.motionY;
            boolean relative = sdk.motionRelative;
            waitMs(60);
            assertEquals(x, sdk.motionX);
            assertEquals(y, sdk.motionY);
            assertEquals(relative, sdk.motionRelative);
        }
    }

    @Test public void disablingTheSavedPreferenceRestoresImmediateResponse() {
        Settings settings = new Settings(controller.get());
        assertTrue(settings.liftOffProtection());
        settings.liftOffProtection(false);
        settings.mouseSensitivity(1);
        callInstanceMethod(controller.get(), "applySettingsToSurface");
        assertFalse(new Settings(controller.get()).liftOffProtection());
        touch(1000, MotionEvent.ACTION_DOWN, 100, 200);
        touch(1040, MotionEvent.ACTION_MOVE, 150, 200);
        touch(1140, MotionEvent.ACTION_MOVE, 151, 200);
        assertEquals(851, sdk.mouseX);
        touch(1141, MotionEvent.ACTION_UP, 151, 200);
    }

    private static void waitMs(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
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
