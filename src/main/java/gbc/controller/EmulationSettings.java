package gbc.controller;

import java.util.concurrent.TimeUnit;

import gbc.controller.config.AppConfig;

final class EmulationSettings {

    int targetFps() {
        return Math.max(1, AppConfig.get().getInt("emulator.frameRate", 60));
    }

    long frameTimeNs() {
        return 1_000_000_000L / targetFps();
    }

    String syncMode() {
        return AppConfig.get().getStringLower("emulator.syncMode", "hybrid");
    }

    boolean throttleEnabled() {
        String mode = syncMode();
        if ("none".equals(mode) || "audio".equals(mode)) {
            return false;
        }
        return AppConfig.get().getBoolean("emulator.throttle", true);
    }

    int frameSkip() {
        return Math.max(0, AppConfig.get().getInt("video.frameskip", 0));
    }

    int renderInterval() {
        return Math.max(1, frameSkip() + 1);
    }

    boolean audioSyncEnabled() {
        String mode = syncMode();
        return "audio".equals(mode) || "hybrid".equals(mode);
    }

    long audioSyncIdleNs() {
        int latencyMs = AppConfig.get().getInt("audio.latencyMs", 150);
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
        return AppConfig.get().getInt("audio.bufferSize", 4096);
    }
}
