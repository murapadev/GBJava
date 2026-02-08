package gbc.controller;

import java.util.concurrent.TimeUnit;

import gbc.controller.config.AppConfig;
import gbc.controller.config.EmulatorConfig;

final class EmulationSettings {

    private EmulatorConfig cfg() {
        return AppConfig.get().getConfig();
    }

    int targetFps() {
        return Math.max(1, cfg().getFrameRate());
    }

    long frameTimeNs() {
        return 1_000_000_000L / targetFps();
    }

    String syncMode() {
        String mode = cfg().getSyncMode();
        return mode == null ? "hybrid" : mode.toLowerCase();
    }

    boolean throttleEnabled() {
        String mode = syncMode();
        if ("none".equals(mode) || "audio".equals(mode)) {
            return false;
        }
        return cfg().isThrottle();
    }

    int frameSkip() {
        return Math.max(0, cfg().getFrameskip());
    }

    int renderInterval() {
        return Math.max(1, frameSkip() + 1);
    }

    boolean audioSyncEnabled() {
        String mode = syncMode();
        return "audio".equals(mode) || "hybrid".equals(mode);
    }

    long audioSyncIdleNs() {
        int latencyMs = cfg().getLatencyMs();
        if (latencyMs < 1) {
            latencyMs = 150;
        }
        return TimeUnit.MILLISECONDS.toNanos(latencyMs);
    }

    long audioSyncSleepNs() {
        String mode = syncMode();
        if ("hybrid".equals(mode)) {
            return TimeUnit.MICROSECONDS.toNanos(500);
        }
        return TimeUnit.MILLISECONDS.toNanos(1);
    }

    int audioSyncThresholdDivisor() {
        String mode = syncMode();
        if ("hybrid".equals(mode)) {
            return 3;
        }
        return 2;
    }

    int audioBufferSize() {
        return cfg().getAudioBufferSize();
    }
}
