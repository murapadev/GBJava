package gbc.controller.input;

import net.java.games.input.Component;
import net.java.games.input.ControllerEnvironment;

import gbc.model.input.Controller;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JoystickManager {
    private static final Logger LOGGER = Logger.getLogger(JoystickManager.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollThread;
    private net.java.games.input.Controller device;
    private String deviceName;
    private long lastRescanNs;

    public void start(Controller controller) {
        if (!Boolean.parseBoolean(System.getProperty("input.joystick.enabled", "false"))) {
            return;
        }
        JInputNativeLoader.loadIfNeeded();
        ensureNativeLoaded();
        if (running.getAndSet(true)) {
            return;
        }
        pollThread = new Thread(() -> pollLoop(controller), "Joystick-Poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
        pollThread = null;
        device = null;
    }

    private void pollLoop(Controller controller) {
        long pollDelayMs = Math.max(1, Long.getLong("input.joystick.pollMs", 5L));
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                if (!Boolean.parseBoolean(System.getProperty("input.joystick.enabled", "false"))) {
                    Thread.sleep(50);
                    continue;
                }

                ensureDevice();
                if (device == null) {
                    Thread.sleep(200);
                    continue;
                }

                if (!device.poll()) {
                    device = null;
                    continue;
                }

                float deadzone = Math.max(0f, Math.min(0.95f,
                        Float.parseFloat(System.getProperty("input.deadzone", "0.15"))));

                boolean up = false, down = false, left = false, right = false;
                boolean a = false, b = false, start = false, select = false;

                boolean usePov = Boolean.parseBoolean(System.getProperty("input.joystick.pov", "true"));
                if (usePov) {
                    Component pov = device.getComponent(Component.Identifier.Axis.POV);
                    if (pov != null) {
                        float v = pov.getPollData();
                        if (v != Component.POV.CENTER) {
                            up = (v == Component.POV.UP || v == Component.POV.UP_LEFT || v == Component.POV.UP_RIGHT);
                            down = (v == Component.POV.DOWN || v == Component.POV.DOWN_LEFT || v == Component.POV.DOWN_RIGHT);
                            left = (v == Component.POV.LEFT || v == Component.POV.UP_LEFT || v == Component.POV.DOWN_LEFT);
                            right = (v == Component.POV.RIGHT || v == Component.POV.UP_RIGHT || v == Component.POV.DOWN_RIGHT);
                        }
                    }
                }

                Component xAxis = findAxis(device, System.getProperty("input.joystick.axis.x", "x"));
                Component yAxis = findAxis(device, System.getProperty("input.joystick.axis.y", "y"));
                if (xAxis != null) {
                    float x = xAxis.getPollData();
                    if (Math.abs(x) > deadzone) {
                        if (x < 0) left = true;
                        if (x > 0) right = true;
                    }
                }
                if (yAxis != null) {
                    float y = yAxis.getPollData();
                    if (Math.abs(y) > deadzone) {
                        if (y < 0) up = true;
                        if (y > 0) down = true;
                    }
                }

                a = isButtonPressed(device, System.getProperty("input.joystick.button.a", "0"));
                b = isButtonPressed(device, System.getProperty("input.joystick.button.b", "1"));
                start = isButtonPressed(device, System.getProperty("input.joystick.button.start", "7"));
                select = isButtonPressed(device, System.getProperty("input.joystick.button.select", "6"));

                controller.setUp(up);
                controller.setDown(down);
                controller.setLeft(left);
                controller.setRight(right);
                controller.setA(a);
                controller.setB(b);
                controller.setStart(start);
                controller.setSelect(select);

                Thread.sleep(pollDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Joystick polling error", e);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void ensureDevice() {
        if (device != null) {
            return;
        }
        JInputNativeLoader.loadIfNeeded();
        ensureNativeLoaded();
        long now = System.nanoTime();
        if (now - lastRescanNs < 1_000_000_000L) {
            return;
        }
        lastRescanNs = now;
        String hint = System.getProperty("input.joystick.device", "").toLowerCase(Locale.ROOT);
        net.java.games.input.Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
        net.java.games.input.Controller candidate = null;
        for (net.java.games.input.Controller c : controllers) {
            if (!isGamepad(c)) {
                continue;
            }
            if (!hint.isBlank()) {
                String name = c.getName().toLowerCase(Locale.ROOT);
                if (!name.contains(hint)) {
                    continue;
                }
            }
            candidate = c;
            break;
        }
        device = candidate;
        deviceName = candidate != null ? candidate.getName() : null;
        if (device != null) {
            LOGGER.log(Level.INFO, () -> "Joystick connected: " + deviceName);
        }
    }

    private void ensureNativeLoaded() {
        String path = System.getProperty("input.joystick.nativePath");
        if (path != null && !path.isBlank()) {
            String current = System.getProperty("java.library.path", "");
            if (!current.contains(path)) {
                System.setProperty("java.library.path", current + java.io.File.pathSeparator + path);
            }
        }
        // Loading happens when JInput initializes; nothing else needed here.
    }

    private boolean isGamepad(net.java.games.input.Controller c) {
        if (c == null) return false;
        net.java.games.input.Controller.Type type = c.getType();
        return type == net.java.games.input.Controller.Type.GAMEPAD
                || type == net.java.games.input.Controller.Type.STICK
                || type == net.java.games.input.Controller.Type.WHEEL;
    }

    private Component findAxis(net.java.games.input.Controller c, String id) {
        if (c == null) return null;
        for (Component comp : c.getComponents()) {
            if (!(comp.getIdentifier() instanceof Component.Identifier.Axis)) {
                continue;
            }
            if (comp.getIdentifier().getName().equalsIgnoreCase(id)) {
                return comp;
            }
        }
        return null;
    }

    private boolean isButtonPressed(net.java.games.input.Controller c, String id) {
        if (c == null) return false;
        String suffix = id == null ? "" : id.trim();
        if (suffix.isEmpty()) {
            return false;
        }
        for (Component comp : c.getComponents()) {
            if (!(comp.getIdentifier() instanceof Component.Identifier.Button)) {
                continue;
            }
            String name = comp.getIdentifier().getName();
            if (name.equalsIgnoreCase(suffix) || name.endsWith(suffix)) {
                return comp.getPollData() > 0.5f;
            }
        }
        return false;
    }
}
