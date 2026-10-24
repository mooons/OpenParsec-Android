package parsec.bindings;

import java.nio.charset.StandardCharsets;

public class Parsec {
    static {
        System.loadLibrary("parsec-bindings");
    }

    // ParsecStatus
    public int PARSEC_OK = 0;

    // ParsecMouseButton
    public int MOUSE_L = 1;
    public int MOUSE_MIDDLE = 2;
    public int MOUSE_R = 3;

    private long parsec; // Parsec *
    private long state; // struct client_state *

    public synchronized native void setLogCallback();
    public synchronized native void init();
    public synchronized native void destroy();
    /** Connect with explicit client config. decoderSoftware forces software
     *  decode; resolutionX/Y + refreshRate request a host resolution (only
     *  honored when this client is the owner's first connection — i.e. you're
     *  streaming your own PC). Pass 0 for resolution/refresh to leave the host
     *  untouched. */
    public synchronized native int clientConnect(String sessionID, String peerID,
                                    int decoderSoftware,
                                    int resolutionX, int resolutionY, int refreshRate);
    public synchronized native void clientDestroy();
    public synchronized native void clientSetDimensions(int x, int y);
    public synchronized native int clientGLRenderFrame();
    public synchronized native void clientGLDestroy();
    public synchronized native void clientSetMuted(boolean muted);
    public synchronized native int clientGetStatus();
    public synchronized native int version();
    public synchronized native int clientSendMouseMotion(boolean relative, int x, int y);
    public synchronized native int clientSendMouseButton(int button, boolean pressed);
    public synchronized native int clientSendMouseWheel(int x, int y);
    public synchronized native int clientSendKeyboard(int keyCode, int keyMod, boolean pressed);
    public synchronized native int clientSendGamepadButton(int gamepadID, int button, boolean pressed);
    public synchronized native int clientSendGamepadAxis(int gamepadID, int axis, int value);
    public synchronized native int clientSendGamepadUnplug(int gamepadID);
    /** True if the SDK is currently reporting a network failure on the client
     *  side (no host frames, transport down). Used by the activity-level
     *  health watchdog to trigger an auto-reconnect. */
    public synchronized native boolean clientHasNetworkFailure();
    public synchronized native long clientGetFreezeSignal();
    // ---- Stats overlay metrics ----
    public synchronized native float clientGetDecodeLatency();   // ms
    public synchronized native float clientGetNetworkLatency();  // ms (round-trip)
    public synchronized native float clientGetEncodeLatency();   // ms
    public synchronized native boolean clientDecoderFellBack();  // true if SW-decode fallback occurred

    // ---- Client event pump (call once per render frame) ----
    /** Drain pending client events (cursor mode, rumble, host user-data). */
    public synchronized native void clientPollEvents();
    /** True when the host has requested relative (pointer-lock / FPS) mouse mode. */
    public synchronized native boolean clientGetCursorRelative();
    /** Packed (motorBig<<8 | motorSmall) if a new rumble is pending, else -1. */
    public synchronized native int clientPollRumble();
    /** relative, modeChanged, imageChanged, x, y, width, height, hotX, hotY, then ARGB pixels. */
    public synchronized native int[] clientPollCursor();
    public synchronized native byte[] clientPollUserData(int id);
    private synchronized native int clientSendUserDataBytes(int id, byte[] utf8);

    public static final int CLIPBOARD_MSG_ID = 1;

    public synchronized int clientSendUserData(int id, String text) {
        if (text == null || text.indexOf('\0') >= 0) return -1;
        return clientSendUserDataBytes(id, text.getBytes(StandardCharsets.UTF_8));
    }

    // ParsecGamepadButton
    public static final int GAMEPAD_BUTTON_A          = 0;
    public static final int GAMEPAD_BUTTON_B          = 1;
    public static final int GAMEPAD_BUTTON_X          = 2;
    public static final int GAMEPAD_BUTTON_Y          = 3;
    public static final int GAMEPAD_BUTTON_BACK       = 4;
    public static final int GAMEPAD_BUTTON_GUIDE      = 5;
    public static final int GAMEPAD_BUTTON_START      = 6;
    public static final int GAMEPAD_BUTTON_LSTICK     = 7;
    public static final int GAMEPAD_BUTTON_RSTICK     = 8;
    public static final int GAMEPAD_BUTTON_LSHOULDER  = 9;
    public static final int GAMEPAD_BUTTON_RSHOULDER  = 10;
    public static final int GAMEPAD_BUTTON_DPAD_UP    = 11;
    public static final int GAMEPAD_BUTTON_DPAD_DOWN  = 12;
    public static final int GAMEPAD_BUTTON_DPAD_LEFT  = 13;
    public static final int GAMEPAD_BUTTON_DPAD_RIGHT = 14;

    // ParsecGamepadAxis
    public static final int GAMEPAD_AXIS_LX       = 0;
    public static final int GAMEPAD_AXIS_LY       = 1;
    public static final int GAMEPAD_AXIS_RX       = 2;
    public static final int GAMEPAD_AXIS_RY       = 3;
    public static final int GAMEPAD_AXIS_TRIGGERL = 4;
    public static final int GAMEPAD_AXIS_TRIGGERR = 5;
}
