package gbc.controller;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Handles keyboard input mapping, debouncing, and repeats for the emulator.
 */
final class InputCoordinator {
    private final ConcurrentLinkedQueue<InputEvent> queue = new ConcurrentLinkedQueue<>();
    private final InputRepeater repeater = new InputRepeater();
    private final InputFilter filter = new InputFilter();

    private static final Map<Integer, String> KEY_CODE_NAMES = buildKeyCodeNames();

    private final BiConsumer<String, Boolean> gameActionHandler;
    private final Consumer<String> uiActionHandler;

    InputCoordinator(BiConsumer<String, Boolean> gameActionHandler, Consumer<String> uiActionHandler) {
        this.gameActionHandler = gameActionHandler;
        this.uiActionHandler = uiActionHandler;
    }

    /**
     * Re-reads input timing settings (debounce, repeat delay/rate, min press)
     * from system properties. Called after the user applies new settings.
     */
    void reconfigure() {
        repeater.reconfigure();
        filter.reconfigure();
    }

    void enqueueKey(int keyCode, boolean pressed) {
        enqueueKey(keyCode, pressed, 0);
    }

    void enqueueKey(int keyCode, boolean pressed, int modifiersEx) {
        queue.add(new InputEvent(keyCode, pressed, modifiersEx, System.nanoTime()));
    }

    void process(long nowNs) {
        InputEvent evt;
        while ((evt = queue.poll()) != null) {
            handleKey(evt);
        }
        repeater.tick(this::handleKeyRepeat);
        filter.tick(gameActionHandler, nowNs);
    }

    private void handleKey(InputEvent evt) {
        String mapped = resolveInputMapping(evt);
        if (mapped == null) {
            return;
        }
        if (isRepeatable(mapped)) {
            repeater.handle(mapped, evt.pressed);
        }
        if (filter.isGameAction(mapped)) {
            filter.handle(mapped, evt.pressed, evt.timeNs, gameActionHandler);
            return;
        }
        if (evt.pressed) {
            uiActionHandler.accept(mapped);
        }
    }

    private void handleKeyRepeat(String action) {
        gameActionHandler.accept(action, true);
    }

    private boolean isRepeatable(String action) {
        return switch (action) {
            case "up", "down", "left", "right", "a", "b" -> true;
            default -> false;
        };
    }

    private String resolveInputMapping(InputEvent evt) {
        int keyCode = evt.keyCode;
        String keyName = normalizeKeyName(keyCode);
        String comboKeyName = buildComboKeyName(keyName, evt.modifiersEx);
        String mapping = comboKeyName != null ? System.getProperty("input.key." + comboKeyName) : null;
        if (mapping != null && !mapping.isBlank()) {
            return mapping.toLowerCase();
        }
        mapping = System.getProperty("input.key." + keyName);
        if (mapping != null && !mapping.isBlank()) {
            return mapping.toLowerCase();
        }
        return switch (keyCode) {
            case KeyEvent.VK_UP -> "up";
            case KeyEvent.VK_DOWN -> "down";
            case KeyEvent.VK_LEFT -> "left";
            case KeyEvent.VK_RIGHT -> "right";
            case KeyEvent.VK_Z -> "a";
            case KeyEvent.VK_X -> "b";
            case KeyEvent.VK_ENTER -> "start";
            case KeyEvent.VK_SPACE -> "select";
            case KeyEvent.VK_P -> "pause";
            case KeyEvent.VK_R -> hasCtrl(evt.modifiersEx) ? "reset" : null;
            case KeyEvent.VK_F4 -> "vram";
            case KeyEvent.VK_F11 -> "fullscreen";
            case KeyEvent.VK_F12 -> "debug";
            case KeyEvent.VK_F5 -> "save0";
            case KeyEvent.VK_F6 -> "save1";
            case KeyEvent.VK_F7 -> "save2";
            case KeyEvent.VK_F8 -> "load0";
            case KeyEvent.VK_F9 -> "load1";
            case KeyEvent.VK_F10 -> "load2";
            default -> null;
        };
    }

    private boolean hasCtrl(int modifiersEx) {
        return (modifiersEx & KeyEvent.CTRL_DOWN_MASK) != 0;
    }

    private static String normalizeKeyName(int keyCode) {
        String name = KEY_CODE_NAMES.get(keyCode);
        if (name != null) {
            return name;
        }
        String keyText = KeyEvent.getKeyText(keyCode);
        return keyText.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static Map<Integer, String> buildKeyCodeNames() {
        Map<Integer, String> names = new HashMap<>();
        Field[] fields = KeyEvent.class.getFields();
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || field.getType() != int.class) {
                continue;
            }
            String fieldName = field.getName();
            if (!fieldName.startsWith("VK_")) {
                continue;
            }
            try {
                int code = field.getInt(null);
                String name = fieldName.substring(3);
                names.putIfAbsent(code, name);
            } catch (IllegalAccessException ignored) {
                // Ignore inaccessible fields
            }
        }
        return names;
    }

    private String buildComboKeyName(String keyName, int modifiersEx) {
        StringBuilder sb = new StringBuilder();
        if ((modifiersEx & KeyEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("CTRL_");
        }
        if ((modifiersEx & KeyEvent.ALT_DOWN_MASK) != 0) {
            sb.append("ALT_");
        }
        if ((modifiersEx & KeyEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("SHIFT_");
        }
        if ((modifiersEx & KeyEvent.META_DOWN_MASK) != 0) {
            sb.append("META_");
        }
        if (sb.length() == 0) {
            return null;
        }
        sb.append(keyName);
        return sb.toString();
    }

    private static final class InputEvent {
        private final int keyCode;
        private final boolean pressed;
        private final int modifiersEx;
        private final long timeNs;

        private InputEvent(int keyCode, boolean pressed, int modifiersEx, long timeNs) {
            this.keyCode = keyCode;
            this.pressed = pressed;
            this.modifiersEx = modifiersEx;
            this.timeNs = timeNs;
        }
    }

    private static final class InputRepeater {
        private long repeatDelayNs;
        private long repeatRateNs;

        private final java.util.Map<String, Long> pressedAt = new java.util.HashMap<>();
        private final java.util.Map<String, Long> lastRepeat = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> down = new java.util.HashMap<>();

        InputRepeater() {
            reconfigure();
        }

        void reconfigure() {
            repeatDelayNs = TimeUnit.MILLISECONDS.toNanos(
                    Math.max(0, Integer.getInteger("input.repeatDelayMs", 250)));
            repeatRateNs = TimeUnit.MILLISECONDS.toNanos(
                    Math.max(1, Integer.getInteger("input.repeatRateMs", 40)));
        }

        void handle(String action, boolean pressed) {
            if (pressed) {
                down.put(action, true);
                pressedAt.put(action, System.nanoTime());
                lastRepeat.put(action, 0L);
            } else {
                down.put(action, false);
                pressedAt.remove(action);
                lastRepeat.remove(action);
            }
        }

        void tick(Consumer<String> repeatConsumer) {
            long now = System.nanoTime();
            for (java.util.Map.Entry<String, Boolean> entry : down.entrySet()) {
                if (!Boolean.TRUE.equals(entry.getValue())) {
                    continue;
                }
                String action = entry.getKey();
                Long pressedAtNs = pressedAt.get(action);
                if (pressedAtNs == null) {
                    continue;
                }
                long held = now - pressedAtNs;
                if (held < repeatDelayNs) {
                    continue;
                }
                long last = lastRepeat.getOrDefault(action, 0L);
                if (last == 0L || now - last >= repeatRateNs) {
                    lastRepeat.put(action, now);
                    repeatConsumer.accept(action);
                }
            }
        }
    }

    private static final class InputFilter {
        private long debounceNs;
        private long minPressNs;

        private final java.util.Map<String, Boolean> state = new java.util.HashMap<>();
        private final java.util.Map<String, Long> lastEvent = new java.util.HashMap<>();
        private final java.util.Map<String, Long> pressTime = new java.util.HashMap<>();
        private final java.util.Map<String, Long> pendingReleaseAt = new java.util.HashMap<>();

        InputFilter() {
            reconfigure();
        }

        void reconfigure() {
            debounceNs = TimeUnit.MILLISECONDS.toNanos(
                    Math.max(0, Integer.getInteger("input.debounceMs", 10)));
            minPressNs = TimeUnit.MILLISECONDS.toNanos(
                    Math.max(0, Integer.getInteger("input.minPressMs", 20)));
        }

        boolean isGameAction(String action) {
            return switch (action) {
                case "up", "down", "left", "right", "a", "b", "start", "select" -> true;
                default -> false;
            };
        }

        void handle(String action, boolean pressed, long timeNs, BiConsumer<String, Boolean> gameHandler) {
            Boolean current = state.get(action);
            long last = lastEvent.getOrDefault(action, 0L);
            if (current != null && current == pressed && (timeNs - last) < debounceNs) {
                return;
            }
            lastEvent.put(action, timeNs);

            if (pressed) {
                state.put(action, true);
                pressTime.put(action, timeNs);
                pendingReleaseAt.remove(action);
                gameHandler.accept(action, true);
            } else {
                Long pressedAt = pressTime.get(action);
                if (pressedAt != null) {
                    long held = timeNs - pressedAt;
                    if (held < minPressNs) {
                        pendingReleaseAt.put(action, pressedAt + minPressNs);
                        return;
                    }
                }
                state.put(action, false);
                pressTime.remove(action);
                gameHandler.accept(action, false);
            }
        }

        void tick(BiConsumer<String, Boolean> gameHandler, long nowNs) {
            java.util.Iterator<java.util.Map.Entry<String, Long>> it = pendingReleaseAt.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, Long> entry = it.next();
                if (nowNs >= entry.getValue()) {
                    String action = entry.getKey();
                    state.put(action, false);
                    pressTime.remove(action);
                    gameHandler.accept(action, false);
                    it.remove();
                }
            }
        }
    }
}
