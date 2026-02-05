package gbc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class InputCoordinatorTest {

    @Test
    void f4TriggersVramViewerAction() {
        List<String> uiActions = new ArrayList<>();
        InputCoordinator coordinator = new InputCoordinator((action, pressed) -> {
        }, uiActions::add);

        coordinator.enqueueKey(KeyEvent.VK_F4, true);
        coordinator.process(System.nanoTime());

        assertEquals(List.of("vram"), uiActions);
    }

    @Test
    void f11TriggersFullscreenAction() {
        List<String> uiActions = new ArrayList<>();
        InputCoordinator coordinator = new InputCoordinator((action, pressed) -> {
        }, uiActions::add);

        coordinator.enqueueKey(KeyEvent.VK_F11, true);
        coordinator.process(System.nanoTime());

        assertTrue(uiActions.contains("fullscreen"));
    }

    @Test
    void resetRequiresControlModifier() {
        List<String> uiActions = new ArrayList<>();
        InputCoordinator coordinator = new InputCoordinator((action, pressed) -> {
        }, uiActions::add);

        coordinator.enqueueKey(KeyEvent.VK_R, true, 0);
        coordinator.process(System.nanoTime());
        assertTrue(uiActions.isEmpty(), "Plain R should not trigger reset");

        coordinator.enqueueKey(KeyEvent.VK_R, true, KeyEvent.CTRL_DOWN_MASK);
        coordinator.process(System.nanoTime());
        assertTrue(uiActions.contains("reset"), "CTRL+R should trigger reset");
    }
}
