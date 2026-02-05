package gbc.view;

import gbc.controller.config.AppConfig;

final class ViewSettings {
    private final AppConfig config = AppConfig.get();

    int scale() {
        int scale = config.getInt("video.scale", config.getInt("emulator.windowScale", 2));
        return Math.max(1, Math.min(8, scale));
    }

    boolean integerScale() {
        return config.getBoolean("video.integerScale", true);
    }

    boolean scanlines() {
        return config.getBoolean("video.scanlines", false);
    }

    boolean smoothScaling() {
        String filter = config.getStringLower("video.filter", "none");
        return "linear".equals(filter);
    }

    EmulatorView.ColorFilter palette() {
        String palette = config.getStringLower("video.palette", "none");
        return switch (palette) {
            case "green", "dmg_green" -> EmulatorView.ColorFilter.GREEN_MONOCHROME;
            case "custom", "sepia" -> EmulatorView.ColorFilter.SEPIA;
            case "high_contrast" -> EmulatorView.ColorFilter.HIGH_CONTRAST;
            default -> EmulatorView.ColorFilter.NONE;
        };
    }
}
