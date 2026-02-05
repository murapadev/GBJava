package gbc.model.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Synchronous event bus for intra-emulation communication.
 * All listeners are invoked on the calling thread (the emulation thread).
 */
public class EventBus {
    private final List<Consumer<EmulatorEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<EmulatorEvent> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<EmulatorEvent> listener) {
        listeners.remove(listener);
    }

    public void publish(EmulatorEvent event) {
        for (int i = 0, n = listeners.size(); i < n; i++) {
            listeners.get(i).accept(event);
        }
    }
}
