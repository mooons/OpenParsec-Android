package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.time.Duration;

import java.io.ByteArrayOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import parsec.bindings.Parsec;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {CursorRenderingTest.ShadowParsec.class, CursorRenderingTest.ShadowSurface.class})
public class CursorRenderingTest {
    @Test public void hostImageRemainsVisibleAfterAnImageOnlyUpdate() {
        Intent intent = new Intent()
                .putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session")
                .putExtra(HostListActivity.EXTRA_PEER_ID, "test-peer");
        try (var controller = Robolectric.buildActivity(ParsecActivity.class, intent).create()) {
            ParsecActivity activity = controller.get();
            View cursor = getField(activity, "cursorView");
            ShadowParsec sdk = org.robolectric.shadow.api.Shadow.extract((Parsec) getField(activity, "parsec"));
            Bitmap source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            source.eraseColor(0xFFFFFFFF);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            source.compress(Bitmap.CompressFormat.PNG, 100, encoded);
            sdk.png = encoded.toByteArray();
            // The bundled SDK returns zero RGBA pixels for this same visible PNG.
            sdk.cursor = new int[] {0, 0, 1, 120, 80, 2, 2, 0, 0, 0, 0, 0, 0};
            callInstanceMethod(activity, "tickIoPump");
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals(View.VISIBLE, cursor.getVisibility());
            Bitmap displayed = ((BitmapDrawable) cursor.getBackground()).getBitmap();
            assertEquals("The cursor must contain visible host pixels", 0xFFFFFFFF, displayed.getPixel(0, 0));

            sdk.cursor = new int[] {0, 0, 1, 0, 0, 65535, 65535, 0, 0};
            callInstanceMethod(activity, "tickIoPump");
            assertSame(displayed, ((BitmapDrawable) cursor.getBackground()).getBitmap());
            sdk.cursor = new int[] {0, 0, 1, 0, 0, 2, 2, 0, 0};
            sdk.png = new byte[] {1, 2, 3};
            callInstanceMethod(activity, "tickIoPump");
            assertSame(displayed, ((BitmapDrawable) cursor.getBackground()).getBitmap());
        }
    }

    @Test public void absoluteInputsAndPointerLockUseTheScaledHostHotspot() {
        Intent intent = new Intent()
                .putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session")
                .putExtra(HostListActivity.EXTRA_PEER_ID, "test-peer");
        try (var controller = Robolectric.buildActivity(ParsecActivity.class, intent).setup()) {
            ParsecActivity activity = controller.get();
            Settings settings = getField(activity, "settings");
            settings.cursorScale(2);
            ClientGLSurface surface = getField(activity, "surface");
            View cursor = getField(activity, "cursorView");
            ShadowParsec sdk = org.robolectric.shadow.api.Shadow.extract((Parsec) getField(activity, "parsec"));
            surface.setTrackpadMode(false);
            surface.onClientResize(800, 600);
            Bitmap source = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888);
            source.eraseColor(0xFFFFFFFF);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            source.compress(Bitmap.CompressFormat.PNG, 100, encoded);
            sdk.png = encoded.toByteArray();
            sdk.cursor = new int[] {0, 0, 1, 0, 0, 4, 6, 1, 2};
            callInstanceMethod(activity, "tickIoPump");
            shadowOf(Looper.getMainLooper()).idle();

            MotionEvent mouse = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 300, 200, 0);
            mouse.setSource(InputDevice.SOURCE_MOUSE);
            assertTrue(surface.onGenericMotionEvent(mouse));
            mouse.recycle();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue("surface attached", surface.isAttachedToWindow());
            assertEquals(View.VISIBLE, cursor.getVisibility());
            assertEquals(298, cursor.getTranslationX(), 0.01);
            assertEquals(196, cursor.getTranslationY(), 0.01);

            MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 400, 300, 0);
            surface.onTouchEvent(down);
            down.recycle();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(70));
            MotionEvent move = MotionEvent.obtain(0, 70, MotionEvent.ACTION_MOVE, 420, 320, 0);
            surface.onTouchEvent(move);
            move.recycle();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(418, cursor.getTranslationX(), 0.01);
            assertEquals(316, cursor.getTranslationY(), 0.01);
            MotionEvent up = MotionEvent.obtain(0, 80, MotionEvent.ACTION_UP, 420, 320, 0);
            surface.onTouchEvent(up);
            up.recycle();

            sdk.relative = true;
            sdk.cursor = new int[] {1, 1, 0, 420, 320, 4, 6, 1, 2};
            callInstanceMethod(activity, "tickIoPump");
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, cursor.getVisibility());
            sdk.relative = false;
            sdk.cursor = new int[] {0, 1, 0, 400, 300, 4, 6, 1, 2};
            callInstanceMethod(activity, "tickIoPump");
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, cursor.getVisibility());
            assertEquals(398, cursor.getTranslationX(), 0.01);
            assertEquals(296, cursor.getTranslationY(), 0.01);

            settings.cursorScale(1.5f);
            callInstanceMethod(activity, "applySettingsToSurface");
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(6, cursor.getLayoutParams().width);
            assertEquals(9, cursor.getLayoutParams().height);
            assertEquals(398.5, cursor.getTranslationX(), 0.01);
            assertEquals(297, cursor.getTranslationY(), 0.01);
        }
    }

    @Implements(value = Parsec.class, isInAndroidSdk = false, callThroughByDefault = false)
    public static class ShadowParsec {
        int[] cursor;
        byte[] png;
        boolean relative;

        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected int[] clientPollCursor() {
            int[] value = cursor;
            cursor = null;
            return value;
        }
        @Implementation protected byte[] clientGetCursorPng() { return png; }
        @Implementation protected boolean clientGetCursorRelative() { return relative; }
        @Implementation protected int clientPollRumble() { return -1; }
    }

    @Implements(value = ClientGLSurface.class, isInAndroidSdk = false)
    public static class ShadowSurface extends org.robolectric.shadows.ShadowGLSurfaceView {
        // Exercise real input and cursor callbacks without starting a native GL thread.
        @Implementation protected void renderInit() {}
        @Implementation protected void onResume() {}
        @Implementation protected void onPause() {}
    }
}
