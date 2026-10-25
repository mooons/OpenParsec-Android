package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.view.View;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {CursorRenderingTest.ShadowParsec.class, CursorRenderingTest.ShadowSurface.class})
public class SessionMenuTest {
    @Test public void statsMenuTogglesTheHudAndSharesTheExistingSetting() {
        Intent intent = new Intent()
                .putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session")
                .putExtra(HostListActivity.EXTRA_PEER_ID, "test-peer");
        try (var controller = Robolectric.buildActivity(ParsecActivity.class, intent).setup()) {
            ParsecActivity activity = controller.get();
            Settings settings = new Settings(activity);
            View hud = getField(activity, "statsView");
            assertFalse(settings.showStats());
            assertEquals(View.GONE, hud.getVisibility());

            item(activity, "Show performance stats").action.run();
            assertTrue(settings.showStats());
            assertEquals(View.VISIBLE, hud.getVisibility());
            item(activity, "Hide performance stats").action.run();
            assertFalse(settings.showStats());
            assertEquals(View.GONE, hud.getVisibility());

            callInstanceMethod(activity, "openSettings");
            settings.showStats(true);
            callInstanceMethod(activity, "closeSettings");
            assertEquals(View.VISIBLE, hud.getVisibility());
            item(activity, "Hide performance stats").action.run();
            assertFalse(settings.showStats());
            assertEquals(View.GONE, hud.getVisibility());
        }
    }

    private static SessionFab.Item item(ParsecActivity activity, String label) {
        SessionFab fab = getField(activity, "fab");
        List<SessionFab.Item> items = getField(fab, "items");
        return items.stream().filter(item -> label.equals(item.label)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing session menu item: " + label));
    }
}
