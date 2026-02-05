package gbc.controller;

/**
 * Snapshot of emulator state needed by the UI.
 */
public record EmulatorStatus(boolean romLoaded, String romTitle, float speedMultiplier, boolean paused) {
}
