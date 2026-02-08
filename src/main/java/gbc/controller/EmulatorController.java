package gbc.controller;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
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

import gbc.controller.audio.AudioEngine;
import gbc.controller.config.AppConfig;
import gbc.controller.config.ConfigSerializer;
import gbc.controller.config.EmulatorConfig;
import gbc.controller.input.JoystickManager;
import gbc.controller.io.RomLoader;
import gbc.model.GameBoyColor;
import gbc.model.cartridge.Cartridge;
import gbc.view.EmulatorUi;
import gbc.view.EmulatorWindow;

/**
 * Coordinates the Swing UI and emulator core, delegating to
 * {@link EmulationLoop} for frame execution and {@link SaveStateManager}
 * for save/load operations.
 */
public class EmulatorController implements EmulatorActions {
    private static final Logger LOGGER = Logger.getLogger(EmulatorController.class.getName());

    private final GameBoyColor gbc;
    private final EmulatorUi view;
    private final EmulationSettings settings;

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
    private final DiagnosticsService diagnostics;
    private final SimpleEmulatorStatusPublisher statusPublisher;
    private final RomLoader romLoader = new RomLoader();
    private final SaveStateManager saveStateManager;
    private final InputCoordinator inputCoordinator;
    private EmulationLoop emulationLoop;
    private ScheduledFuture<?> fpsTask;
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private KeyEventDispatcher keyDispatcher;

    public EmulatorController(GameBoyColor gbc, EmulatorUi view) {
        this.gbc = Objects.requireNonNull(gbc, "gbc");
        this.view = Objects.requireNonNullElseGet(view, () -> new EmulatorWindow(this.gbc));
        this.diagnostics = new EmulatorDiagnostics(this.gbc, emulationLock);
        this.statusPublisher = new SimpleEmulatorStatusPublisher();
        this.settings = new EmulationSettings();
        this.inputCoordinator = new InputCoordinator(this::applyGameAction, this::handleUiAction);
        this.saveStateManager = new SaveStateManager(gbc, emulationLock, ioExecutor,
                this::setPausedAndReturnPrevious, this::isCartridgeLoaded);
        this.view.setController(this);
        setupKeyListener();
    }

    public EmulatorController() {
        this(new GameBoyColor(), null);
    }

    private boolean setPausedAndReturnPrevious(boolean desired) {
        return setPaused(desired);
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
                    inputCoordinator.enqueueKey(keyCode, true, event.getModifiersEx());
                }
            } else if (event.getID() == KeyEvent.KEY_RELEASED) {
                pressedKeys.remove(keyCode);
                inputCoordinator.enqueueKey(keyCode, false, event.getModifiersEx());
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

        emulationLoop = new EmulationLoop(
                gbc, emulationLock, running, paused, frameCounter,
                settings, audioEngine,
                () -> SwingUtilities.invokeLater(() -> {
                    try {
                        view.update();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "UI update error", e);
                    }
                }),
                () -> inputCoordinator.process(System.nanoTime()));
        emulationLoop.start();

        startMetricsTask();
        joystickManager.start(gbc.getController());
        audioEngine.start(gbc);
        audioEngine.setPaused(false);
        publishStatus();
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
            default -> {
            }
        }
    }

    private void handleUiAction(String action) {
        switch (action) {
            case "pause" -> togglePause();
            case "reset" -> reset();
            case "debug" -> view.openDebugView();
            case "vram" -> view.openVRAMViewer();
            case "fullscreen" -> view.toggleFullscreen();
            case "save0" -> saveState(0);
            case "save1" -> saveState(1);
            case "save2" -> saveState(2);
            case "load0" -> loadState(0);
            case "load1" -> loadState(1);
            case "load2" -> loadState(2);
            default -> {
            }
        }
    }

    @Override
    public CompletableFuture<Void> loadRomAsync(String path) {
        Objects.requireNonNull(path, "path");
        boolean wasPaused = setPaused(true);

        return CompletableFuture.runAsync(() -> {
            emulationLock.lock();
            try {
                Cartridge cartridge = romLoader.load(java.nio.file.Path.of(path));
                gbc.insertCartridge(cartridge);
                gbc.reset();
            } catch (java.io.IOException e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = "Failed to load ROM: " + path;
                }
                throw new RuntimeException(message, e);
            } finally {
                emulationLock.unlock();
            }
        }, ioExecutor).whenComplete((unused, throwable) -> {
            if (throwable == null) {
                frameCounter.set(0);
                lastFrameSample.set(0);
                SwingUtilities.invokeLater(() -> {
                    view.update();
                    String romName = new File(path).getName();
                    String currentPath = gbc.getCurrentRomPath();
                    if (currentPath != null && !currentPath.isBlank()) {
                        romName = new File(currentPath).getName();
                    }
                    view.setTitle("Game Boy Color Emulator - " + romName);
                });
                publishStatus();
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

    @Override
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
        publishStatus();

        if (!wasPaused) {
            setPaused(false);
        }

        LOGGER.info("Emulator reset");
    }

    @Override
    public void togglePause() {
        boolean currentlyPaused = paused.get();
        setPaused(!currentlyPaused);
        LOGGER.info(currentlyPaused ? "Emulator resumed" : "Emulator paused");
    }

    @Override
    public void pause() {
        setPaused(true);
    }

    @Override
    public void resume() {
        setPaused(false);
    }

    @Override
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
        publishStatus();
        return previous;
    }

    @Override
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

    public void saveState(int slot) {
        boolean success = saveStateManager.saveState(slot);
        final String message;
        if (!isCartridgeLoaded()) {
            message = "No ROM loaded";
        } else if (gbc.getCurrentRomPath() == null) {
            message = "Cannot save: ROM path unknown";
        } else {
            message = success
                    ? String.format("State saved to slot %d", slot)
                    : String.format("Failed to save state to slot %d", slot);
        }
        SwingUtilities.invokeLater(() -> view.setStatusText(message));
    }

    public void loadState(int slot) {
        boolean success = saveStateManager.loadState(slot);
        final String message;
        if (!isCartridgeLoaded()) {
            message = "No ROM loaded";
        } else if (gbc.getCurrentRomPath() == null) {
            message = "Cannot load: ROM path unknown";
        } else {
            message = success
                    ? String.format("State loaded from slot %d", slot)
                    : String.format("Failed to load state from slot %d", slot);
        }
        SwingUtilities.invokeLater(() -> {
            view.setStatusText(message);
            if (success) {
                view.update();
            }
        });
    }

    @Override
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

        if (emulationLoop != null) {
            emulationLoop.stop();
        }

        ioExecutor.shutdownNow();
        metricsExecutor.shutdownNow();

        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
            pressedKeys.clear();
        }

        gbc.shutdown();
        LOGGER.info("Emulator stopped");
    }

    @Override
    public void setSpeedMultiplier(float speed) {
        gbc.setSpeedMultiplier(speed);
        SwingUtilities.invokeLater(() -> view.onSpeedChanged(speed));
        publishStatus();
    }

    @Override
    public EmulatorStatus getStatus() {
        boolean romLoaded = gbc.getMemory().isCartridgeLoaded();
        String title = romLoaded ? readRomTitle() : null;
        return new EmulatorStatus(romLoaded, title, gbc.getSpeedMultiplier(), paused.get());
    }

    @Override
    public DiagnosticsService diagnostics() {
        return diagnostics;
    }

    @Override
    public EmulatorStatusPublisher statusPublisher() {
        return statusPublisher;
    }

    @Override
    public void restartAudio() {
        audioEngine.restart(gbc);
        LOGGER.info("Audio engine restarted with updated settings");
    }

    @Override
    public void reloadInputConfig() {
        inputCoordinator.reconfigure();
        LOGGER.info("Input configuration reloaded");
    }

    @Override
    public void applyConfig(EmulatorConfig config) {
        AppConfig.get().setConfig(config);
        ConfigSerializer.forceApplyToSystemProperties(config);
        audioEngine.restart(gbc);
        inputCoordinator.reconfigure();
        LOGGER.info("Configuration applied to all subsystems");
    }

    @Override
    public CompletableFuture<Boolean> saveStateToAsync(java.nio.file.Path path) {
        Objects.requireNonNull(path, "path");
        if (!isCartridgeLoaded()) {
            SwingUtilities.invokeLater(() -> view.setStatusText("No ROM loaded"));
            return CompletableFuture.completedFuture(false);
        }
        return saveStateManager.saveStateToAsync(path).whenComplete((success, throwable) -> {
            String message = (throwable == null && Boolean.TRUE.equals(success))
                    ? "State saved: " + path.getFileName()
                    : "Failed to save state";
            SwingUtilities.invokeLater(() -> view.setStatusText(message));
        });
    }

    @Override
    public CompletableFuture<Boolean> loadStateFromAsync(java.nio.file.Path path) {
        Objects.requireNonNull(path, "path");
        if (!isCartridgeLoaded()) {
            SwingUtilities.invokeLater(() -> view.setStatusText("No ROM loaded"));
            return CompletableFuture.completedFuture(false);
        }
        if (!java.nio.file.Files.exists(path)) {
            SwingUtilities.invokeLater(() -> view.setStatusText("Save state not found"));
            return CompletableFuture.completedFuture(false);
        }
        return saveStateManager.loadStateFromAsync(path).whenComplete((success, throwable) -> {
            SwingUtilities.invokeLater(() -> {
                if (throwable == null && Boolean.TRUE.equals(success)) {
                    view.setStatusText("State loaded: " + path.getFileName());
                    view.update();
                } else {
                    view.setStatusText("Failed to load state");
                }
            });
        });
    }

    private String readRomTitle() {
        try {
            int romByte = gbc.getMemory().readByte(0x0134);
            if (romByte == 0) {
                return null;
            }
            StringBuilder title = new StringBuilder();
            for (int i = 0x0134; i <= 0x0143; i++) {
                int value = gbc.getMemory().readByte(i) & 0xFF;
                if (value == 0) {
                    break;
                }
                if (value >= 32 && value <= 126) {
                    title.append((char) value);
                }
            }
            String parsed = title.toString().trim();
            return parsed.isBlank() ? null : parsed;
        } catch (Exception e) {
            return null;
        }
    }

    private void publishStatus() {
        statusPublisher.publish(getStatus());
    }
}
