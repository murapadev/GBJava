package gbc.view;

import gbc.controller.EmulatorActions;
import gbc.controller.config.EmulatorConfig;

/**
 * View contract for the emulator UI. Keeps controller dependencies on abstractions.
 */
public interface EmulatorUi {
    void setController(EmulatorActions controller);

    void setVisible(boolean visible);

    void repaint();

    void update();

    void updateFPS(double fps);

    void onPauseStateChanged(boolean paused);

    void onSpeedChanged(float speed);

    void setTitle(String title);

    void setStatusText(String text);

    void openDebugView();

    void openVRAMViewer();

    void toggleFullscreen();

    /** Push video/display settings from the given config to the view. */
    default void applyDisplayConfig(EmulatorConfig config) {}
}
