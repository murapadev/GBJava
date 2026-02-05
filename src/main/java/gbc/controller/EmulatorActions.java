package gbc.controller;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * UI-facing controller contract so views depend on abstractions, not concrete controllers.
 */
public interface EmulatorActions {
    CompletableFuture<Void> loadRomAsync(String path);

    void togglePause();

    void pause();

    void resume();

    boolean isPaused();

    void reset();

    void stepInstruction();

    void stop();

    void setSpeedMultiplier(float speed);

    CompletableFuture<Boolean> saveStateToAsync(Path path);

    CompletableFuture<Boolean> loadStateFromAsync(Path path);

    EmulatorStatus getStatus();

    DiagnosticsService diagnostics();

    EmulatorStatusPublisher statusPublisher();
}
