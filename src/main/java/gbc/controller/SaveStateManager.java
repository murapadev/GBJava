package gbc.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.controller.io.SaveState;
import gbc.model.GameBoyColor;

/**
 * Manages save/load state operations, decoupled from UI concerns.
 */
public class SaveStateManager {
    private static final Logger LOGGER = Logger.getLogger(SaveStateManager.class.getName());

    private final GameBoyColor gbc;
    private final ReentrantLock emulationLock;
    private final ExecutorService ioExecutor;
    private final Function<Boolean, Boolean> setPaused;
    private final BooleanSupplier isCartridgeLoaded;

    public SaveStateManager(GameBoyColor gbc,
            ReentrantLock emulationLock,
            ExecutorService ioExecutor,
            Function<Boolean, Boolean> setPaused,
            BooleanSupplier isCartridgeLoaded) {
        this.gbc = gbc;
        this.emulationLock = emulationLock;
        this.ioExecutor = ioExecutor;
        this.setPaused = setPaused;
        this.isCartridgeLoaded = isCartridgeLoaded;
    }

    public boolean saveState(int slot) {
        if (!isCartridgeLoaded.getAsBoolean()) {
            return false;
        }

        String romPath = gbc.getCurrentRomPath();
        if (romPath == null) {
            return false;
        }

        boolean wasPaused = setPaused.apply(true);
        emulationLock.lock();
        try {
            Path savePath = SaveState.getSaveStatePath(romPath, slot);
            try {
                Files.createDirectories(savePath.getParent());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to create save directory", e);
            }
            return SaveState.save(gbc, savePath);
        } finally {
            emulationLock.unlock();
            if (!wasPaused) {
                setPaused.apply(false);
            }
        }
    }

    public boolean loadState(int slot) {
        if (!isCartridgeLoaded.getAsBoolean()) {
            return false;
        }

        String romPath = gbc.getCurrentRomPath();
        if (romPath == null) {
            return false;
        }

        Path savePath = SaveState.getSaveStatePath(romPath, slot);
        if (savePath == null || !Files.exists(savePath)) {
            return false;
        }

        boolean wasPaused = setPaused.apply(true);
        emulationLock.lock();
        try {
            return SaveState.load(gbc, savePath);
        } finally {
            emulationLock.unlock();
            if (!wasPaused) {
                setPaused.apply(false);
            }
        }
    }

    public CompletableFuture<Boolean> saveStateToAsync(Path path) {
        if (!isCartridgeLoaded.getAsBoolean()) {
            return CompletableFuture.completedFuture(false);
        }
        boolean wasPaused = setPaused.apply(true);
        return CompletableFuture.supplyAsync(() -> {
            emulationLock.lock();
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    try {
                        Files.createDirectories(parent);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to create save directory", e);
                    }
                }
                return SaveState.save(gbc, path);
            } finally {
                emulationLock.unlock();
            }
        }, ioExecutor).whenComplete((success, throwable) -> {
            if (!wasPaused) {
                setPaused.apply(false);
            }
            if (throwable != null) {
                LOGGER.log(Level.SEVERE, "Failed to save state", throwable);
            }
        });
    }

    public CompletableFuture<Boolean> loadStateFromAsync(Path path) {
        if (!isCartridgeLoaded.getAsBoolean()) {
            return CompletableFuture.completedFuture(false);
        }
        if (!Files.exists(path)) {
            return CompletableFuture.completedFuture(false);
        }
        boolean wasPaused = setPaused.apply(true);
        return CompletableFuture.supplyAsync(() -> {
            emulationLock.lock();
            try {
                return SaveState.load(gbc, path);
            } finally {
                emulationLock.unlock();
            }
        }, ioExecutor).whenComplete((success, throwable) -> {
            if (!wasPaused) {
                setPaused.apply(false);
            }
            if (throwable != null) {
                LOGGER.log(Level.SEVERE, "Failed to load state", throwable);
            }
        });
    }
}
