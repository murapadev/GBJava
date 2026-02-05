package gbc.controller;

import java.util.function.Consumer;

/**
 * Publishes emulator status updates to UI consumers.
 */
public interface EmulatorStatusPublisher {
    void addListener(Consumer<EmulatorStatus> listener);

    void removeListener(Consumer<EmulatorStatus> listener);
}
