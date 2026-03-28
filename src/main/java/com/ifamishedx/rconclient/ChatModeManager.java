package com.ifamishedx.rconclient;

/**
 * Tracks whether the player is in RCON Chat Mode or normal (All Chat) mode.
 *
 * <p>In <b>RCON Chat Mode</b> plain text typed in chat is intercepted and
 * forwarded to the RCON server instead of the Minecraft server.
 */
public class ChatModeManager {

    /** {@code true} while the player is in RCON Chat Mode. */
    private boolean rconMode = false;

    public boolean isRconMode() {
        return rconMode;
    }

    /** Switch to RCON Chat Mode. */
    public void enableRconMode() {
        this.rconMode = true;
    }

    /** Switch back to All Chat (normal) mode. */
    public void disableRconMode() {
        this.rconMode = false;
    }

    /** Return a human-readable label for the current mode. */
    public String getModeLabel() {
        return rconMode ? "RC (RCON Chat Mode)" : "AC (All Chat Mode)";
    }
}
