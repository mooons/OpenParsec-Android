package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "land")
public class HostListLayoutTest {
    @Test public void existingHostEntriesFitAfterRotationAndWindowResize() {
        Intent intent = new Intent().putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session");
        try (var controller = Robolectric.buildActivity(HostListActivity.class, intent)) {
            setField(controller.get(), "refreshing", true);
            HostListActivity activity = controller.setup().get();
            LinearLayout hosts = getField(activity, "hostsContainer");
            View card = callInstanceMethod(activity, "makeHostCard", ClassParameter.from(
                    ParsecApi.Host.class, new ParsecApi.Host("test-peer", "Test desktop", "User", true)));
            hosts.addView(card);
            View root = getField(activity, "root");
            float density = activity.getResources().getDisplayMetrics().density;
            Configuration landscape = new Configuration(activity.getResources().getConfiguration());
            Configuration portrait = new Configuration(landscape);
            portrait.orientation = Configuration.ORIENTATION_PORTRAIT;
            portrait.screenWidthDp = 360;
            portrait.screenHeightDp = 800;

            for (int width : new int[]{800, 360, 800, 280}) {
                controller.configurationChange(width < 400 ? portrait : landscape);
                shadowOf(Looper.getMainLooper()).idle();
                int px = Math.round(width * density);
                int height = Math.round(600 * density);
                root.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, px, height);
                assertTrue("Host card's left edge must fit a " + width + "dp window", card.getLeft() >= 0);
                assertTrue("Host card's right edge must fit a " + width + "dp window",
                        card.getRight() <= hosts.getWidth());
                assertEquals(hosts.getWidth(), card.getWidth());
                assertEquals("Entries should shrink with the window but stop at 540dp",
                        Math.min(Math.round(540 * density), px - Math.round(32 * density)), card.getWidth());
                View listParent = (View) hosts.getParent();
                assertEquals("The bounded list should stay centered", listParent.getWidth() / 2f,
                        hosts.getX() + hosts.getWidth() / 2f, 1f);
                ViewGroup inner = (ViewGroup) ((ViewGroup) card).getChildAt(0);
                View connect = inner.getChildAt(inner.getChildCount() - 1);
                assertTrue("Connect must remain inside the card", connect.getRight() <= card.getWidth());
                assertTrue("Connect must stay usable", connect.getWidth() > 0);
                assertSame(card, hosts.getChildAt(0));
            }
        }
    }

    @Test public void landscapeTabsAreCompactAndRotationRestoresPortraitLabels() {
        Intent intent = new Intent().putExtra(LoginActivity.EXTRA_SESSION_ID, "test-session");
        try (var controller = Robolectric.buildActivity(HostListActivity.class, intent)) {
            // Keep this layout check independent of the host/friends API.
            setField(controller.get(), "refreshing", true);
            setField(controller.get(), "friendsLoaded", true);
            HostListActivity activity = controller.setup().get();
            BottomNavigationView nav = getField(activity, "bottomTabs");
            float density = activity.getResources().getDisplayMetrics().density;
            assertEquals(Math.round(48 * density), nav.getHeight());
            assertEquals(NavigationBarView.LABEL_VISIBILITY_UNLABELED, nav.getLabelVisibilityMode());
            assertEquals("Hosts", nav.getMenu().findItem(R.id.nav_hosts).getTitle());
            assertEquals("Friends", nav.getMenu().findItem(R.id.nav_friends).getTitle());

            nav.setSelectedItemId(R.id.nav_friends);
            View friends = getField(activity, "friendsScroll");
            assertEquals(View.VISIBLE, friends.getVisibility());
            Configuration landscape = new Configuration(activity.getResources().getConfiguration());
            Configuration portrait = new Configuration(landscape);
            portrait.orientation = Configuration.ORIENTATION_PORTRAIT;
            portrait.screenWidthDp = landscape.screenHeightDp;
            portrait.screenHeightDp = landscape.screenWidthDp;
            controller.configurationChange(portrait);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(NavigationBarView.LABEL_VISIBILITY_AUTO, nav.getLabelVisibilityMode());
            assertEquals(Math.round(80 * density), nav.getHeight());
            assertEquals(R.id.nav_friends, nav.getSelectedItemId());
            assertEquals(View.VISIBLE, friends.getVisibility());

            controller.configurationChange(landscape);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Math.round(48 * density), nav.getHeight());
        }
    }
}
