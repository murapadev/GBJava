package gbc.model.event;

import gbc.model.cartridge.Cartridge;

/**
 * Sealed event hierarchy for emulator-internal notifications.
 *
 * <p>Events stay in the model layer; view/controller code reacts to them
 * through the emulator's event bus. New event types can be added here
 * without touching UI code — consumers simply ignore events they don't
 * handle.
 */
public sealed interface EmulatorEvent
        permits EmulatorEvent.VBlank,
                EmulatorEvent.ModeChange,
                EmulatorEvent.InterruptRequest,
                EmulatorEvent.CartridgeLoaded,
                EmulatorEvent.FrameComplete,
                EmulatorEvent.ScanlineComplete,
                EmulatorEvent.AudioBufferReady,
                EmulatorEvent.DebugBreakpoint {

    record VBlank() implements EmulatorEvent {}

    record ModeChange(int oldMode, int newMode, int ly) implements EmulatorEvent {}

    record InterruptRequest(int bit) implements EmulatorEvent {}

    record CartridgeLoaded(Cartridge cart) implements EmulatorEvent {}

    /** Emitted after the PPU finishes rendering a complete frame. */
    record FrameComplete(long frameNumber) implements EmulatorEvent {}

    /** Emitted when LY advances to a new scanline. */
    record ScanlineComplete(int ly) implements EmulatorEvent {}

    /** Emitted when the APU fills an audio sample buffer ready for playback. */
    record AudioBufferReady(float[] leftSamples, float[] rightSamples) implements EmulatorEvent {}

    /** Emitted when a debug breakpoint condition is hit. */
    record DebugBreakpoint(int pc, String reason) implements EmulatorEvent {}
}
