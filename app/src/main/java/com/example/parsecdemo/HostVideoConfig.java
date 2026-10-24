package com.example.parsecdemo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The desktop host's user-data protocol, shared with the iOS client. */
final class HostVideoConfig {
    static final int GET_VIDEO = 9;
    static final int GET_DISPLAYS = 10;
    static final int SET_VIDEO = 11;
    static final int DISPLAYS = 12;
    static final int[] BITRATES = {0, 3, 5, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50};

    private JSONObject config;
    private JSONArray displays = new JSONArray();
    private int defaultBitrate;

    boolean receive(int id, String text) throws JSONException {
        if (text == null || text.length() > 1_048_576) throw new JSONException("Invalid host message size");
        if (id == SET_VIDEO) {
            JSONObject incoming = new JSONObject(text);
            JSONArray video = incoming.getJSONArray("video");
            if (video.length() == 0 || video.length() > 3) throw new JSONException("Invalid video streams");
            JSONObject first = video.getJSONObject(0);
            checkRange(first.optInt("resolutionX", 0), 0, 16384);
            checkRange(first.optInt("resolutionY", 0), 0, 16384);
            checkRange(first.getInt("encoderMaxBitrate"), 1, 1000);
            if (config == null) defaultBitrate = first.getInt("encoderMaxBitrate");
            config = incoming;
            return true;
        }
        if (id == DISPLAYS) {
            JSONArray incoming = new JSONArray(text);
            if (incoming.length() > 64) throw new JSONException("Too many displays");
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject display = incoming.getJSONObject(i);
                String displayId = display.getString("id");
                if (displayId.isEmpty() || displayId.length() > 4096) throw new JSONException("Invalid display ID");
            }
            displays = incoming;
            return true;
        }
        return false;
    }

    boolean available() { return config != null; }
    int displayCount() { return displays.length(); }
    int width() { return available() ? first().optInt("resolutionX", 0) : 0; }
    int height() { return available() ? first().optInt("resolutionY", 0) : 0; }
    int bitrate() { return available() ? first().optInt("encoderMaxBitrate", 0) : 0; }
    boolean constantFps() { return available() && first().optBoolean("fullFPS", false); }
    String output() { return available() ? first().optString("output", "none") : "none"; }

    String[] displayLabels() throws JSONException {
        String[] labels = new String[displays.length() + 1];
        labels[0] = "Auto";
        for (int i = 0; i < displays.length(); i++) {
            JSONObject d = displays.getJSONObject(i);
            labels[i + 1] = d.optString("name", "Display " + (i + 1)) + " " + d.optString("adapterName", "");
        }
        return labels;
    }

    String displayId(int index) throws JSONException {
        return index == 0 ? "none" : displays.getJSONObject(index - 1).getString("id");
    }

    String update(int width, int height, int bitrate, int fps, boolean constantFps,
                  String output) throws JSONException {
        if (!available()) throw new JSONException("Waiting for host video configuration");
        checkRange(width, 0, 16384);
        checkRange(height, 0, 16384);
        if ((width == 0) != (height == 0)) throw new JSONException("Incomplete resolution");
        checkRange(bitrate, 0, 1000);
        checkRange(fps, 0, 1000);
        boolean validOutput = "none".equals(output) || output().equals(output);
        for (int i = 0; i < displays.length(); i++) validOutput |= displayId(i + 1).equals(output);
        if (!validOutput) throw new JSONException("Unknown display");
        // Preserve host fields and other streams; only change the requested stream's controls.
        JSONObject updated = new JSONObject(config.toString());
        JSONObject first = updated.getJSONArray("video").getJSONObject(0);
        first.put("resolutionX", width);
        first.put("resolutionY", height);
        first.put("encoderMaxBitrate", bitrate == 0 ? defaultBitrate : bitrate);
        first.put("encoderFPS", fps);
        first.put("fullFPS", constantFps);
        first.put("output", output);
        return updated.toString();
    }

    private JSONObject first() { return config.optJSONArray("video").optJSONObject(0); }

    private static void checkRange(int value, int min, int max) throws JSONException {
        if (value < min || value > max) throw new JSONException("Host value out of range");
    }
}
