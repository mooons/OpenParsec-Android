package com.example.parsecdemo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.*;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;

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
