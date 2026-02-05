package gbc.model.event;

import gbc.model.cartridge.Cartridge;

public sealed interface EmulatorEvent
        permits EmulatorEvent.VBlank,
                EmulatorEvent.ModeChange,
                EmulatorEvent.InterruptRequest,
                EmulatorEvent.CartridgeLoaded {
    // TODO: Expand event set for PPU/audio/debug without leaking UI concerns into model.

    record VBlank() implements EmulatorEvent {}

    record ModeChange(int oldMode, int newMode, int ly) implements EmulatorEvent {}

    record InterruptRequest(int bit) implements EmulatorEvent {}

    record CartridgeLoaded(Cartridge cart) implements EmulatorEvent {}
}
