package com.example.parsecdemo;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)

public class HostVideoConfigTest {
    @Test public void updatesOnlyTheFirstStreamAndRejectsBadInput() throws Exception {
        HostVideoConfig c = new HostVideoConfig();
        assertTrue(c.receive(HostVideoConfig.SET_VIDEO, "{\"video\":[{\"resolutionX\":1920,\"resolutionY\":1080,\"encoderMaxBitrate\":20,\"fullFPS\":false,\"output\":\"none\"}]}"));
        String updated = c.update(1280, 720, 30, 60, true, "none");
        assertTrue(updated.contains("\"resolutionX\":1280"));
        assertTrue(updated.contains("\"encoderMaxBitrate\":30"));
        try { c.receive(HostVideoConfig.SET_VIDEO, "{\"video\":[{\"resolutionX\":99999}]} "); fail(); }
        catch (org.json.JSONException expected) {}
    }

    @Test public void statusPolicyDoesNotRetryPermanentFailures() {
        assertFalse(SessionStatus.shouldRetry(-13000, 0, true));
        assertTrue(SessionStatus.shouldRetry(-6101, 0, true));
        assertFalse(SessionStatus.shouldRetry(-6101, 3, true));
    }
}
