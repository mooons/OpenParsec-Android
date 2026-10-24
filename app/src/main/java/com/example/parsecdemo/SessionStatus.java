package com.example.parsecdemo;

/** Status values from the bundled v1 SDK, not the newer iOS ABI. */
final class SessionStatus {
    static final int OK = 0;
    static final int APPROVAL = 6;
    static final int NOT_RUNNING = -3;

    static boolean permanent(int status) {
        switch (status) {
            case 4: case 5: case 8: case 9: // host shutdown/kick and declined/cancelled requests
            case -3008: // authentication
            case -12018: // unsupported network
            case -13000: case -13008: case -13009: case -13011: case -13012:
            case -10: case -11: case -17: case -18: case -20: // decoder configuration
            case -36000: case -36001: // SDK ABI mismatch
                return true;
            default: return false;
        }
    }

    static String readable(int status) {
        switch (status) {
            case 0: return "Connected";
            case 4: return "Host shut down";
            case 5: return "Disconnected by the host";
            case 6: return "Waiting for the host to approve the connection";
            case 8: return "Host declined the connection";
            case 9: return "Connection cancelled";
            case 99: return "Host is offline";
            case -3: return "Connection is not running";
            case -3008: return "Session expired; sign in again";
            case -6101: case -6105: return "Cannot reach Parsec";
            case -3002: case -3003: case -3009: return "Lost the connection to Parsec";
            case -6023: return "Cannot reach the host";
            case -6024: case -6033: return "No network path to the host";
            case 2000: case -12007: return "Connection timed out";
            case -12017: return "Host ended the connection";
            case -12018: return "Network is not supported";
            case -12019: return "Connection was interrupted";
            case -13000: return "Host has no display";
            case -13008: case -13009: return "Host cannot stream at this resolution";
            case -13011: return "No one is signed in on the host";
            case -13012: return "Host is full";
            case -10: case -11: case -17: case -18: case -20: return "Video decoder is unavailable or incompatible";
            case -36000: case -36001: return "Parsec SDK version is incompatible";
            default: return "Connection failed (code " + status + ")";
        }
    }

    static boolean shouldRetry(int status, int attempts, boolean enabled) {
        return enabled && attempts < 3 && !permanent(status) && status != APPROVAL;
    }
}
