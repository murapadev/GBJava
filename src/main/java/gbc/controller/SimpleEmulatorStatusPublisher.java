package gbc.controller;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class SimpleEmulatorStatusPublisher implements EmulatorStatusPublisher {
    private final List<Consumer<EmulatorStatus>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void addListener(Consumer<EmulatorStatus> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Consumer<EmulatorStatus> listener) {
        listeners.remove(listener);
    }

    void publish(EmulatorStatus status) {
        for (Consumer<EmulatorStatus> listener : listeners) {
            listener.accept(status);
        }
    }
}
