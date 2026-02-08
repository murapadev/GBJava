package gbc.controller;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.controller.audio.AudioEngine;
import gbc.model.GameBoyColor;

/**
 * Runs the emulation loop on a dedicated thread, executing frames at the
 * configured frame rate and invoking a callback when the UI should be updated.
 */
public class EmulationLoop {
    private static final Logger LOGGER = Logger.getLogger(EmulationLoop.class.getName());

    private final GameBoyColor gbc;
    private final ReentrantLock emulationLock;
    private final AtomicBoolean running;
    private final AtomicBoolean paused;
    private final AtomicLong frameCounter;
    private final EmulationSettings settings;
    private final AudioEngine audioEngine;
    private final Runnable uiUpdateCallback;
    private final Runnable inputProcessCallback;
    private Thread emulationThread;

    public EmulationLoop(GameBoyColor gbc,
            ReentrantLock emulationLock,
            AtomicBoolean running,
            AtomicBoolean paused,
            AtomicLong frameCounter,
            EmulationSettings settings,
            AudioEngine audioEngine,
            Runnable uiUpdateCallback,
            Runnable inputProcessCallback) {
        this.gbc = gbc;
        this.emulationLock = emulationLock;
        this.running = running;
        this.paused = paused;
        this.frameCounter = frameCounter;
        this.settings = settings;
        this.audioEngine = audioEngine;
        this.uiUpdateCallback = uiUpdateCallback;
        this.inputProcessCallback = inputProcessCallback;
    }

    public void start() {
        emulationThread = new Thread(this::runEmulationLoop, "EmulationThread");
        emulationThread.setDaemon(true);
        emulationThread.start();
    }

    public void stop() {
        if (emulationThread != null) {
            emulationThread.interrupt();
            try {
                emulationThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
                long frameTimeNs = settings.frameTimeNs();
                boolean throttle = settings.throttleEnabled();
                int renderInterval = settings.renderInterval();

                if (!throttle || deltaTime >= frameTimeNs) {
                    emulationLock.lock();
                    try {
                        executeFrame();
                    } finally {
                        emulationLock.unlock();
                    }

                    frameCounter.incrementAndGet();
                    framesSinceUiUpdate++;

                    if (framesSinceUiUpdate >= renderInterval) {
                        framesSinceUiUpdate = 0;
                        uiUpdateCallback.run();
                    }

                    lastTime = currentTime;
                } else {
                    long sleepNs = frameTimeNs - deltaTime;
                    if (sleepNs > 0) {
                        TimeUnit.NANOSECONDS.sleep(Math.min(
                                sleepNs,
                                TimeUnit.MILLISECONDS.toNanos(2)));
                    } else {
                        Thread.yield();
                    }
                }

                // Secondary audio sync: the primary synchronisation is APU ring-buffer
                // back-pressure (Apu.enqueueFilledBuffer blocks when full). This block
                // adds a gentle throttle based on audio pipeline headroom.
                if (settings.audioSyncEnabled()) {
                    long lastWrite = audioEngine.getLastWriteNs();
                    int available = audioEngine.getLastAvailableBytes();
                    if (lastWrite > 0) {
                        if (System.nanoTime() - lastWrite > settings.audioSyncIdleNs()) {
                            TimeUnit.NANOSECONDS.sleep(settings.audioSyncSleepNs());
                        } else if (available > 0) {
                            int bufferSize = settings.audioBufferSize();
                            int threshold = bufferSize / settings.audioSyncThresholdDivisor();
                            if (available > threshold) {
                                TimeUnit.NANOSECONDS.sleep(settings.audioSyncSleepNs());
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
        if (gbc.getMemory().isCartridgeLoaded()) {
            inputProcessCallback.run();
            final int cyclesPerFrame = (int) (70224 * gbc.getEffectiveSpeedMultiplier());
            int executedCycles = 0;
            int cycleCount = 0;

            while (executedCycles < cyclesPerFrame) {
                try {
                    int cycles = gbc.executeCycle();
                    executedCycles += cycles;
                    cycleCount++;

                    // Safety check: prevent infinite loops (check only every 1024 instructions)
                    if ((cycleCount & 0x3FF) == 0) {
                        int maxInstructions = Math.max(100000, (cyclesPerFrame / 4) + 2048);
                        if (cycleCount > maxInstructions) {
                            int lastPc = gbc.getCpu().getRegisters().getPC() & 0xFFFF;
                            final int finalCycleCount = cycleCount;
                            final int finalExecutedCycles = executedCycles;
                            final int finalCyclesPerFrame = cyclesPerFrame;
                            LOGGER.log(Level.WARNING, () -> String.format(
                                    "Too many cycles in frame (%d). Executed=%d target=%d lastPC=0x%04X",
                                    finalCycleCount, finalExecutedCycles, finalCyclesPerFrame, lastPc));
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error during CPU execution", e);
                    break;
                }
            }
        } else {
            try {
                gbc.getPpu().updateGraphics();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error updating PPU", e);
            }
        }
    }
}
