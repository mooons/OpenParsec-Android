package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;

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
@Config(sdk = 34, shadows = CursorRenderingTest.ShadowParsec.class)
public class CursorRenderingTest {
    @Test public void hostImageRemainsVisibleAfterAnImageOnlyUpdate() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
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

    @Implements(value = Parsec.class, isInAndroidSdk = false, callThroughByDefault = false)
    public static class ShadowParsec {
        int[] cursor;
        byte[] png;

        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected int[] clientPollCursor() {
            int[] value = cursor;
            cursor = null;
            return value;
        }
        @Implementation protected byte[] clientGetCursorPng() { return png; }
        @Implementation protected int clientPollRumble() { return -1; }
    }
}
