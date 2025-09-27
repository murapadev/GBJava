package gbc.ui.controller;

import gbc.model.GameBoyColor;
import gbc.ui.audio.AudioEngine;
import gbc.ui.view.EmulatorWindow;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.Objects;
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

/**
 * Coordinates the Swing UI and emulator core, handling threading concerns so
 * heavy operations (ROM loading, frame execution, metrics) never block the EDT.
 */
public class EmulatorController {
    private static final int TARGET_FPS = 60;
    private static final long FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS;

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
    private ScheduledFuture<?> fpsTask;
    private Thread emulationThread;

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
        view.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyRelease(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // Not used
            }
        });

        view.setFocusable(true);
        view.requestFocusInWindow();
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
                System.err.println("Error showing UI: " + e.getMessage());
                e.printStackTrace();
            }
        });

        emulationThread = new Thread(this::runEmulationLoop, "EmulationThread");
        emulationThread.setDaemon(true);
        emulationThread.start();

        startMetricsTask();
        audioEngine.start(gbc);
        audioEngine.setPaused(false);
        System.out.println("Emulator started");
    }

    private void startMetricsTask() {
        fpsTask = metricsExecutor.scheduleAtFixedRate(() -> {
            try {
                long totalFrames = frameCounter.get();
                long previous = lastFrameSample.getAndSet(totalFrames);
                long rendered = Math.max(0, totalFrames - previous);

                SwingUtilities.invokeLater(() -> view.updateFPS(paused.get() ? 0d : (double) rendered));
            } catch (Exception e) {
                System.err.println("Failed to update FPS: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void runEmulationLoop() {
        System.out.println("Emulation loop started");

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

                if (deltaTime >= FRAME_TIME_NS) {
                    emulationLock.lock();
                    try {
                        executeFrame();
                    } finally {
                        emulationLock.unlock();
                    }

                    frameCounter.incrementAndGet();
                    framesSinceUiUpdate++;

                    if (framesSinceUiUpdate >= 10) {
                        framesSinceUiUpdate = 0;
                        SwingUtilities.invokeLater(() -> {
                            try {
                                view.update();
                            } catch (Exception e) {
                                System.err.println("UI update error: " + e.getMessage());
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Emulation loop error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Emulation loop ended");
    }

    private void executeFrame() {
        if (isCartridgeLoaded()) {
            int cyclesPerFrame = 70224;
            int executedCycles = 0;
            int cycleCount = 0;
            long startTime = System.nanoTime();

            while (executedCycles < cyclesPerFrame) {
                try {
                    int cycles = gbc.executeCycle();
                    executedCycles += cycles;
                    cycleCount++;

                    if (cycleCount > 100000) {
                        System.err.println("WARNING: Too many cycles in frame (" + cycleCount + ")");
                        System.err.println("Executed cycles: " + executedCycles + ", Target: " + cyclesPerFrame);
                        System.err.println(
                                "Last PC: " + String.format("0x%04X", (int) gbc.getCpu().getRegisters().getPC()));
                        break;
                    }

                    if (System.nanoTime() - startTime > 100_000_000) {
                        System.err.println("WARNING: Frame execution timeout, possible infinite loop");
                        System.err.println("Executed cycles: " + executedCycles + ", Instructions: " + cycleCount);
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("Error during CPU execution: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }

            if (Math.random() < 0.01) {
                System.out.println("Executed " + cycleCount + " instructions, " + executedCycles + " cycles");
            }
        } else {
            try {
                gbc.getPpu().updateGraphics();
            } catch (Exception e) {
                System.err.println("Error updating PPU: " + e.getMessage());
            }
        }
    }

    private void handleKeyPress(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> gbc.getController().setUp(true);
            case KeyEvent.VK_DOWN -> gbc.getController().setDown(true);
            case KeyEvent.VK_LEFT -> gbc.getController().setLeft(true);
            case KeyEvent.VK_RIGHT -> gbc.getController().setRight(true);
            case KeyEvent.VK_Z -> gbc.getController().setA(true);
            case KeyEvent.VK_X -> gbc.getController().setB(true);
            case KeyEvent.VK_ENTER -> gbc.getController().setStart(true);
            case KeyEvent.VK_SPACE -> gbc.getController().setSelect(true);
            case KeyEvent.VK_P -> togglePause();
            case KeyEvent.VK_R -> reset();
            case KeyEvent.VK_F12 -> view.openDebugView();
        }
    }

    private void handleKeyRelease(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> gbc.getController().setUp(false);
            case KeyEvent.VK_DOWN -> gbc.getController().setDown(false);
            case KeyEvent.VK_LEFT -> gbc.getController().setLeft(false);
            case KeyEvent.VK_RIGHT -> gbc.getController().setRight(false);
            case KeyEvent.VK_Z -> gbc.getController().setA(false);
            case KeyEvent.VK_X -> gbc.getController().setB(false);
            case KeyEvent.VK_ENTER -> gbc.getController().setStart(false);
            case KeyEvent.VK_SPACE -> gbc.getController().setSelect(false);
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
                    System.err.println("Failed to load ROM: " + throwable.getMessage());
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

        System.out.println("Emulator reset");
    }

    public void togglePause() {
        boolean currentlyPaused = paused.get();
        setPaused(!currentlyPaused);
        System.out.println(currentlyPaused ? "Emulator resumed" : "Emulator paused");
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

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        setPaused(true);

        if (fpsTask != null) {
            fpsTask.cancel(true);
        }

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

        System.out.println("Emulator stopping...");
    }

    public GameBoyColor getGameBoyColor() {
        return gbc;
    }

    public EmulatorWindow getView() {
        return view;
    }
}
