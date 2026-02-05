package gbc.view;

import gbc.controller.EmulatorActions;

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
}
