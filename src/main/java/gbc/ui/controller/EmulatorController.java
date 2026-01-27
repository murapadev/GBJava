package gbc.ui.controller;

import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import gbc.core.io.SaveState;
import gbc.core.input.JoystickManager;
import gbc.model.GameBoyColor;
import gbc.ui.audio.AudioEngine;
import gbc.ui.view.EmulatorWindow;

/**
 * Coordinates the Swing UI and emulator core, handling threading concerns so
 * heavy operations (ROM loading, frame execution, metrics) never block the EDT.
 */
public class EmulatorController {
    private static final Logger LOGGER = Logger.getLogger(EmulatorController.class.getName());
    private static final int TARGET_FPS = Integer.getInteger("emulator.frameRate", 60);
    private static final long FRAME_TIME_NS = 1_000_000_000L / Math.max(1, TARGET_FPS);
    private static final String SYNC_MODE = System.getProperty("emulator.syncMode", "auto");
    private static final boolean THROTTLE = resolveThrottle();
    private static final int FRAME_SKIP = Math.max(0, Integer.getInteger("video.frameskip", 0));
    private static final int RENDER_INTERVAL = Math.max(1, FRAME_SKIP + 1);

    private final GameBoyColor gbc;
    private final EmulatorWindow view;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong frameCounter = new AtomicLong();
    private final AtomicLong lastFrameSample = new AtomicLong();

    private final ReentrantLock emulationLock = new ReentrantLock();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Emulator-IO");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService metricsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Emulator-Metrics");
        thread.setDaemon(true);
        return thread;
    });

    private final AudioEngine audioEngine = new AudioEngine();
    private final JoystickManager joystickManager = new JoystickManager();
    private ScheduledFuture<?> fpsTask;
    private Thread emulationThread;
    private static final long AUDIO_SYNC_IDLE_NS = resolveAudioSyncIdleNs();
    private final ConcurrentLinkedQueue<InputEvent> inputQueue = new ConcurrentLinkedQueue<>();
    private final InputRepeater inputRepeater = new InputRepeater();
    private final InputFilter inputFilter = new InputFilter();
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private KeyEventDispatcher keyDispatcher;

    public EmulatorController(GameBoyColor gbc, EmulatorWindow view) {
        this.gbc = Objects.requireNonNull(gbc, "gbc");
        this.view = Objects.requireNonNullElseGet(view, () -> new EmulatorWindow(this.gbc));
        this.view.setController(this);
        setupKeyListener();
    }

    public EmulatorController() {
        this(new GameBoyColor(), null);
    }

    private void setupKeyListener() {
        if (keyDispatcher != null) {
            return;
        }
        keyDispatcher = event -> {
            if (!shouldHandleKeyEvent(event)) {
                return false;
            }
            int keyCode = event.getKeyCode();
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                if (pressedKeys.add(keyCode)) {
                    enqueueInput(keyCode, true);
                }
            } else if (event.getID() == KeyEvent.KEY_RELEASED) {
                pressedKeys.remove(keyCode);
                enqueueInput(keyCode, false);
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    private boolean shouldHandleKeyEvent(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED && event.getID() != KeyEvent.KEY_RELEASED) {
            return false;
        }
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active == view;
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }

        running.set(true);
        paused.set(false);
        frameCounter.set(0);
        lastFrameSample.set(0);

        SwingUtilities.invokeLater(() -> {
            try {
                view.setVisible(true);
                view.repaint();
                view.onPauseStateChanged(false);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error showing UI", e);
            }
        });

        emulationThread = new Thread(this::runEmulationLoop, "EmulationThread");
        emulationThread.setDaemon(true);
        emulationThread.start();

        startMetricsTask();
        joystickManager.start(gbc.getController());
        audioEngine.start(gbc);
        audioEngine.setPaused(false);
        LOGGER.info("Emulator started");
    }

    private void startMetricsTask() {
        fpsTask = metricsExecutor.scheduleAtFixedRate(() -> {
            try {
                long totalFrames = frameCounter.get();
                long previous = lastFrameSample.getAndSet(totalFrames);
                long rendered = Math.max(0, totalFrames - previous);

                SwingUtilities.invokeLater(() -> view.updateFPS(paused.get() ? 0d : (double) rendered));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to update FPS", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void runEmulationLoop() {
        LOGGER.info("Emulation loop started");

        long lastTime = System.nanoTime();
        int framesSinceUiUpdate = 0;

        while (running.get()) {
            try {
                if (paused.get()) {
                    Thread.sleep(2);
                    lastTime = System.nanoTime();
                    continue;
                }

                long currentTime = System.nanoTime();
                long deltaTime = currentTime - lastTime;

                if (!THROTTLE || deltaTime >= FRAME_TIME_NS) {
                    emulationLock.lock();
                    try {
                        executeFrame();
                    } finally {
                        emulationLock.unlock();
                    }

                    frameCounter.incrementAndGet();
                    framesSinceUiUpdate++;

                    if (framesSinceUiUpdate >= RENDER_INTERVAL) {
                        framesSinceUiUpdate = 0;
                        SwingUtilities.invokeLater(() -> {
                            try {
                                view.update();
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "UI update error", e);
                            }
                        });
                    }

                    lastTime = currentTime;
                } else {
                    long sleepNs = FRAME_TIME_NS - deltaTime;
                    if (sleepNs > 0) {
                        TimeUnit.NANOSECONDS.sleep(Math.min(
                                sleepNs,
                                TimeUnit.MILLISECONDS.toNanos(2)));
                    } else {
                        Thread.yield();
                    }
                }

                if (isAudioSyncEnabled()) {
                    long lastWrite = audioEngine.getLastWriteNs();
                    int available = audioEngine.getLastAvailableBytes();
                    if (lastWrite > 0) {
                        if (System.nanoTime() - lastWrite > AUDIO_SYNC_IDLE_NS) {
                            TimeUnit.MILLISECONDS.sleep(1);
                        } else if (available > 0) {
                            int bufferSize = Integer.getInteger("audio.bufferSize", 4096);
                            int threshold = bufferSize / 2;
                            if (available > threshold) {
                                TimeUnit.MILLISECONDS.sleep(1);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Emulation loop error", e);
            }
        }

        LOGGER.info("Emulation loop ended");
    }

    private void executeFrame() {
        if (isCartridgeLoaded()) {
            processInputQueue();
            final int cyclesPerFrame = (int) (70224 * gbc.getEffectiveSpeedMultiplier());
            int executedCycles = 0;
            int cycleCount = 0;
            long startTime = System.nanoTime();

            while (executedCycles < cyclesPerFrame) {
                try {
                    int cycles = gbc.executeCycle();
                    executedCycles += cycles;
                    cycleCount++;

                    if (cycleCount > 100000) {
                        int lastPc = gbc.getCpu().getRegisters().getPC() & 0xFFFF;
                        final int finalCycleCount = cycleCount;
                        final int finalExecutedCycles = executedCycles;
                        final int finalCyclesPerFrame = cyclesPerFrame;
                        LOGGER.log(Level.WARNING, () -> String.format(
                                "Too many cycles in frame (%d). Executed=%d target=%d lastPC=0x%04X",
                                finalCycleCount, finalExecutedCycles, finalCyclesPerFrame, lastPc));
                        break;
                    }

                    if (System.nanoTime() - startTime > 100_000_000) {
                        final int finalExecutedCycles = executedCycles;
                        final int finalCycleCount = cycleCount;
                        LOGGER.log(Level.WARNING, () -> String.format(
                                "Frame execution timeout (executed=%d instructions=%d)",
                                finalExecutedCycles, finalCycleCount));
                        break;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error during CPU execution", e);
                    break;
                }
            }

            if (Math.random() < 0.01) {
                final int executed = executedCycles;
                final int count = cycleCount;
                LOGGER.log(Level.FINE, () -> String.format("Executed %d instructions, %d cycles", count, executed));
            }
        } else {
            try {
                gbc.getPpu().updateGraphics();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error updating PPU", e);
            }
        }
    }

    private static boolean resolveThrottle() {
        String mode = SYNC_MODE == null ? "auto" : SYNC_MODE.toLowerCase();
        if (mode.equals("none")) {
            return false;
        }
        if (mode.equals("audio")) {
            return false;
        }
        return Boolean.parseBoolean(System.getProperty("emulator.throttle", "true"));
    }

    private static boolean isAudioSyncEnabled() {
        String mode = SYNC_MODE == null ? "auto" : SYNC_MODE.toLowerCase();
        return mode.equals("audio");
    }

    private static long resolveAudioSyncIdleNs() {
        int latencyMs = Integer.getInteger("audio.latencyMs", 150);
        if (latencyMs < 1) {
            latencyMs = 150;
        }
        return TimeUnit.MILLISECONDS.toNanos(latencyMs);
    }

    private void enqueueInput(int keyCode, boolean pressed) {
        inputQueue.add(new InputEvent(keyCode, pressed, System.nanoTime()));
    }

    private void processInputQueue() {
        InputEvent evt;
        while ((evt = inputQueue.poll()) != null) {
            handleKey(evt.keyCode, evt.pressed, evt.timeNs);
        }
        inputRepeater.tick(this);
        inputFilter.tick(this, System.nanoTime());
    }

    private void handleKey(int keyCode, boolean pressed, long timeNs) {
        String mapped = resolveInputMapping(keyCode);
        if (mapped == null) {
            return;
        }
        if (isRepeatable(mapped)) {
            inputRepeater.handle(mapped, pressed);
        }
        if (inputFilter.isGameAction(mapped)) {
            inputFilter.handle(mapped, pressed, timeNs, this);
            return;
        }
        switch (mapped) {
            case "pause" -> { if (pressed) togglePause(); }
            case "reset" -> { if (pressed) reset(); }
            case "debug" -> { if (pressed) view.openDebugView(); }
            case "vram" -> { if (pressed) view.openVRAMViewer(); }
            case "fullscreen" -> { if (pressed) view.toggleFullscreen(); }
            case "save0" -> { if (pressed) saveState(0); }
            case "save1" -> { if (pressed) saveState(1); }
            case "save2" -> { if (pressed) saveState(2); }
            case "load0" -> { if (pressed) loadState(0); }
            case "load1" -> { if (pressed) loadState(1); }
            case "load2" -> { if (pressed) loadState(2); }
            default -> { }
        }
    }

    private boolean isRepeatable(String action) {
        return switch (action) {
            case "up", "down", "left", "right", "a", "b" -> true;
            default -> false;
        };
    }

    private String resolveInputMapping(int keyCode) {
        String keyName = KeyEvent.getKeyText(keyCode).toUpperCase().replace(' ', '_');
        String mapping = System.getProperty("input.key." + keyName);
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
            case KeyEvent.VK_R -> "reset";
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

    private static final class InputEvent {
        private final int keyCode;
        private final boolean pressed;
        private final long timeNs;

        private InputEvent(int keyCode, boolean pressed, long timeNs) {
            this.keyCode = keyCode;
            this.pressed = pressed;
            this.timeNs = timeNs;
        }
    }

    private static final class InputRepeater {
        private final long repeatDelayNs = TimeUnit.MILLISECONDS.toNanos(
                Math.max(0, Integer.getInteger("input.repeatDelayMs", 250)));
        private final long repeatRateNs = TimeUnit.MILLISECONDS.toNanos(
                Math.max(1, Integer.getInteger("input.repeatRateMs", 40)));

        private final java.util.Map<String, Long> pressedAt = new java.util.HashMap<>();
        private final java.util.Map<String, Long> lastRepeat = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> down = new java.util.HashMap<>();

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

        void tick(EmulatorController controller) {
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
                    controller.handleKeyRepeat(action);
                }
            }
        }
    }

    private void handleKeyRepeat(String action) {
        applyGameAction(action, true);
    }

    private void applyGameAction(String action, boolean pressed) {
        switch (action) {
            case "up" -> gbc.getController().setUp(pressed);
            case "down" -> gbc.getController().setDown(pressed);
            case "left" -> gbc.getController().setLeft(pressed);
            case "right" -> gbc.getController().setRight(pressed);
            case "a" -> gbc.getController().setA(pressed);
            case "b" -> gbc.getController().setB(pressed);
            case "start" -> gbc.getController().setStart(pressed);
            case "select" -> gbc.getController().setSelect(pressed);
            default -> { }
        }
    }

    private static final class InputFilter {
        private final long debounceNs = TimeUnit.MILLISECONDS.toNanos(
                Math.max(0, Integer.getInteger("input.debounceMs", 10)));
        private final long minPressNs = TimeUnit.MILLISECONDS.toNanos(
                Math.max(0, Integer.getInteger("input.minPressMs", 20)));

        private final java.util.Map<String, Boolean> state = new java.util.HashMap<>();
        private final java.util.Map<String, Long> lastEvent = new java.util.HashMap<>();
        private final java.util.Map<String, Long> pressTime = new java.util.HashMap<>();
        private final java.util.Map<String, Long> pendingReleaseAt = new java.util.HashMap<>();

        boolean isGameAction(String action) {
            return switch (action) {
                case "up", "down", "left", "right", "a", "b", "start", "select" -> true;
                default -> false;
            };
        }

        void handle(String action, boolean pressed, long timeNs, EmulatorController controller) {
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
                controller.applyGameAction(action, true);
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
                controller.applyGameAction(action, false);
            }
        }

        void tick(EmulatorController controller, long nowNs) {
            java.util.Iterator<java.util.Map.Entry<String, Long>> it = pendingReleaseAt.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, Long> entry = it.next();
                if (nowNs >= entry.getValue()) {
                    String action = entry.getKey();
                    state.put(action, false);
                    pressTime.remove(action);
                    controller.applyGameAction(action, false);
                    it.remove();
                }
            }
        }
    }

    public CompletableFuture<Void> loadRomAsync(String path) {
        Objects.requireNonNull(path, "path");
        boolean wasPaused = setPaused(true);

        return CompletableFuture.runAsync(() -> {
            emulationLock.lock();
            try {
                gbc.insertCartridge(path);
                gbc.reset();
            } finally {
                emulationLock.unlock();
            }
        }, ioExecutor).whenComplete((unused, throwable) -> {
            if (throwable == null) {
                frameCounter.set(0);
                lastFrameSample.set(0);
                SwingUtilities.invokeLater(() -> {
                    view.update();
                    view.setTitle("Game Boy Color Emulator - " + new File(path).getName());
                });
                if (!wasPaused) {
                    setPaused(false);
                }
            } else {
                if (!wasPaused) {
                    setPaused(false);
                }
            }
        });
    }

    public void loadRom(String path) {
        CompletableFuture<Void> future = loadRomAsync(path);
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                future.join();
            } catch (CompletionException | CancellationException ex) {
                throw new RuntimeException("Failed to load ROM: " + path,
                        ex.getCause() != null ? ex.getCause() : ex);
            }
        } else {
            future.whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE, "Failed to load ROM", throwable);
                }
            });
        }
    }

    public boolean isCartridgeLoaded() {
        return gbc.getMemory().isCartridgeLoaded();
    }

    public void reset() {
        boolean wasPaused = setPaused(true);
        emulationLock.lock();
        try {
            gbc.reset();
            frameCounter.set(0);
            lastFrameSample.set(0);
        } finally {
            emulationLock.unlock();
        }

        SwingUtilities.invokeLater(view::update);

        if (!wasPaused) {
            setPaused(false);
        }

        LOGGER.info("Emulator reset");
    }

    public void togglePause() {
        boolean currentlyPaused = paused.get();
        setPaused(!currentlyPaused);
        LOGGER.info(currentlyPaused ? "Emulator resumed" : "Emulator paused");
    }

    public void pause() {
        setPaused(true);
    }

    public void resume() {
        setPaused(false);
    }

    public boolean isPaused() {
        return paused.get();
    }

    private boolean setPaused(boolean desired) {
        boolean previous = paused.getAndSet(desired);
        if (desired && !previous) {
            gbc.pause();
        } else if (!desired && previous) {
            gbc.resume();
        }
        audioEngine.setPaused(desired);
        SwingUtilities.invokeLater(() -> view.onPauseStateChanged(desired));
        return previous;
    }

    public void stepInstruction() {
        setPaused(true);
        emulationLock.lock();
        try {
            gbc.executeCycle();
        } finally {
            emulationLock.unlock();
        }

        SwingUtilities.invokeLater(() -> {
            view.update();
            view.setStatusText(String.format("Stepped - PC: $%04X", gbc.getCpu().getRegisters().getPC()));
        });
    }
    
    /**
     * Saves the current emulator state to the specified slot.
     * @param slot The save state slot (0-2)
     */
    public void saveState(int slot) {
        if (!isCartridgeLoaded()) {
            SwingUtilities.invokeLater(() -> view.setStatusText("No ROM loaded"));
            return;
        }
        
        String romPath = gbc.getCurrentRomPath();
        if (romPath == null) {
            SwingUtilities.invokeLater(() -> view.setStatusText("Cannot save: ROM path unknown"));
            return;
        }
        
        boolean wasPaused = setPaused(true);
        emulationLock.lock();
        try {
            java.nio.file.Path savePath = SaveState.getSaveStatePath(romPath, slot);
            try {
                java.nio.file.Files.createDirectories(savePath.getParent());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to create save directory", e);
            }
            boolean success = SaveState.save(gbc, savePath);
            final String message = success 
                ? String.format("State saved to slot %d", slot)
                : String.format("Failed to save state to slot %d", slot);
            SwingUtilities.invokeLater(() -> view.setStatusText(message));
        } finally {
            emulationLock.unlock();
            if (!wasPaused) {
                setPaused(false);
            }
        }
    }
    
    /**
     * Loads emulator state from the specified slot.
     * @param slot The save state slot (0-2)
     */
    public void loadState(int slot) {
        if (!isCartridgeLoaded()) {
            SwingUtilities.invokeLater(() -> view.setStatusText("No ROM loaded"));
            return;
        }
        
        String romPath = gbc.getCurrentRomPath();
        if (romPath == null) {
            SwingUtilities.invokeLater(() -> view.setStatusText("Cannot load: ROM path unknown"));
            return;
        }
        
        java.nio.file.Path savePath = SaveState.getSaveStatePath(romPath, slot);
        if (savePath == null || !java.nio.file.Files.exists(savePath)) {
            final int finalSlot = slot;
            SwingUtilities.invokeLater(() -> view.setStatusText(String.format("No save state in slot %d", finalSlot)));
            return;
        }
        
        boolean wasPaused = setPaused(true);
        emulationLock.lock();
        try {
            boolean success = SaveState.load(gbc, savePath);
            final String message = success 
                ? String.format("State loaded from slot %d", slot)
                : String.format("Failed to load state from slot %d", slot);
            SwingUtilities.invokeLater(() -> {
                view.setStatusText(message);
                view.update();
            });
        } finally {
            emulationLock.unlock();
            if (!wasPaused) {
                setPaused(false);
            }
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        setPaused(true);

        if (fpsTask != null) {
            fpsTask.cancel(true);
        }

        joystickManager.stop();
        audioEngine.stop();

        if (emulationThread != null) {
            emulationThread.interrupt();
            try {
                emulationThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ioExecutor.shutdownNow();
        metricsExecutor.shutdownNow();

        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
            pressedKeys.clear();
        }

        // Save battery-backed SRAM before closing
        gbc.shutdown();

        LOGGER.info("Emulator stopped");
    }

    public GameBoyColor getGameBoyColor() {
        return gbc;
    }

    public EmulatorWindow getView() {
        return view;
    }
}
