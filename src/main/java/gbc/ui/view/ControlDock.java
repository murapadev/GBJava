package gbc.ui.view;

import gbc.ui.controller.EmulatorController;
import gbc.ui.view.EmulatorView.ColorFilter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;

public class ControlDock extends JPanel {

    private static final Color SURFACE = new Color(252, 250, 246);
    private static final Color BORDER = new Color(220, 213, 199);

    private final EmulatorWindow window;
    private final EmulatorView emulatorView;
    private final MenuBar menuBar;
    private final SettingsDialog settingsDialog;

    private EmulatorController controller;

    private final JToggleButton pauseToggle;
    private final JButton stepButton;
    private final JComboBox<SpeedOption> speedCombo;
    private final JComboBox<ColorFilter> filterCombo;
    private final JSpinner scaleSpinner;
    private final JToggleButton scanlinesToggle;
    private final JToggleButton smoothToggle;
    private final JToggleButton aspectToggle;

    private boolean updating;

    private static final SpeedOption[] SPEED_OPTIONS = {
            new SpeedOption("25%", 0.25f),
            new SpeedOption("50%", 0.5f),
            new SpeedOption("100%", 1.0f),
            new SpeedOption("200%", 2.0f),
            new SpeedOption("400%", 4.0f)
    };

    public ControlDock(EmulatorWindow window, EmulatorView emulatorView, MenuBar menuBar, SettingsDialog settingsDialog) {
        this.window = Objects.requireNonNull(window, "window");
        this.emulatorView = Objects.requireNonNull(emulatorView, "emulatorView");
        this.menuBar = menuBar;
        this.settingsDialog = settingsDialog;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(16, 14, 16, 14)));
        setBackground(SURFACE);

        add(sectionTitle("Controls"));
        add(buttonRow("Open ROM", this::openRom));
        pauseToggle = toggleRow("Pause", this::togglePause);
        stepButton = buttonRow("Step", window::stepInstruction);
        add(buttonRow("Reset", this::resetEmulator));
        add(buttonRow("Screenshot", this::takeScreenshot));
        add(Box.createVerticalStrut(12));

        add(sectionTitle("Display"));
        filterCombo = new JComboBox<>(ColorFilter.values());
        add(labeledRow("Filter", filterCombo));
        filterCombo.addActionListener(e -> {
            if (!updating) {
                setColorFilter((ColorFilter) filterCombo.getSelectedItem());
            }
        });

        scaleSpinner = new JSpinner(new SpinnerNumberModel(emulatorView.getScaleFactor(), 1, 8, 1));
        add(labeledRow("Scale", scaleSpinner));
        scaleSpinner.addChangeListener(e -> {
            if (!updating) {
                setScale((Integer) scaleSpinner.getValue());
            }
        });

        scanlinesToggle = toggleRow("Scanlines", this::applyScanlinesToggle);
        smoothToggle = toggleRow("Smooth Scaling", this::applySmoothToggle);
        aspectToggle = toggleRow("Lock Aspect", this::applyAspectToggle);

        add(Box.createVerticalStrut(12));
        add(sectionTitle("Emulation"));
        speedCombo = new JComboBox<>(SPEED_OPTIONS);
        add(labeledRow("Speed", speedCombo));
        speedCombo.addActionListener(e -> {
            if (!updating) {
                SpeedOption option = (SpeedOption) speedCombo.getSelectedItem();
                if (option != null) {
                    setSpeed(option.multiplier);
                }
            }
        });
        add(buttonRow("Debug View", window::openDebugView));
        add(buttonRow("VRAM Viewer", window::openVRAMViewer));
        add(buttonRow("Fullscreen", window::toggleFullscreen));
        add(buttonRow("Settings", this::openSettings));
    }

    public void setController(EmulatorController controller) {
        this.controller = controller;
    }

    public void syncState(boolean paused, float speed, ColorFilter filter,
                          boolean scanlines, boolean smooth, boolean maintainAspect, int scale) {
        updating = true;
        try {
            pauseToggle.setSelected(paused);
            pauseToggle.setText(paused ? "Resume" : "Pause");
            stepButton.setEnabled(paused || controller == null);
            filterCombo.setSelectedItem(filter);
            scanlinesToggle.setSelected(scanlines);
            smoothToggle.setSelected(smooth);
            aspectToggle.setSelected(maintainAspect);
            scaleSpinner.setValue(Math.max(1, Math.min(8, scale)));
            selectSpeed(speed);
        } finally {
            updating = false;
        }
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
        return label;
    }

    private JButton buttonRow(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(220, 32));
        button.addActionListener(e -> {
            if (!updating) {
                action.run();
            }
        });
        add(button);
        add(Box.createVerticalStrut(6));
        return button;
    }

    private JToggleButton toggleRow(String text, Runnable action) {
        JToggleButton toggle = new JToggleButton(text);
        toggle.setAlignmentX(LEFT_ALIGNMENT);
        toggle.setMaximumSize(new Dimension(220, 32));
        toggle.addActionListener(e -> {
            if (!updating) {
                action.run();
            }
        });
        add(toggle);
        add(Box.createVerticalStrut(6));
        return toggle;
    }

    private JPanel labeledRow(String labelText, java.awt.Component component) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(SURFACE);
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(90, 22));
        row.add(label);
        row.add(component);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(240, 32));
        add(row);
        add(Box.createVerticalStrut(8));
        return row;
    }

    private void openRom() {
        if (menuBar != null) {
            menuBar.promptOpenRomDialog();
        } else {
            window.showMessage("Menu is unavailable. Use the CLI to load a ROM.",
                    "Open ROM", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void togglePause() {
        if (menuBar != null) {
            menuBar.togglePauseFromToolbar();
        } else {
            window.togglePause();
        }
    }

    private void resetEmulator() {
        if (menuBar != null) {
            menuBar.resetFromToolbar();
        } else {
            window.resetEmulator();
        }
    }

    private void takeScreenshot() {
        if (menuBar != null) {
            menuBar.takeScreenshotFromToolbar();
        } else {
            emulatorView.takeScreenshot();
        }
    }

    private void setSpeed(float speed) {
        if (menuBar != null) {
            menuBar.setEmulationSpeedFromToolbar(speed);
        } else {
            if (controller != null) {
                controller.getGameBoyColor().setSpeedMultiplier(speed);
            }
            window.onSpeedChanged(speed);
        }
    }

    private void setScale(int scale) {
        if (menuBar != null) {
            menuBar.setScaleFromToolbar(scale);
        } else {
            emulatorView.setScaleFactor(scale);
            window.onDisplaySettingsChanged(true);
        }
    }

    private void setScanlines(boolean enabled) {
        emulatorView.setShowScanlines(enabled);
        window.onDisplaySettingsChanged(true);
    }

    private void setSmoothScaling(boolean enabled) {
        emulatorView.setSmoothScaling(enabled);
        window.onDisplaySettingsChanged(true);
    }

    private void setMaintainAspect(boolean maintain) {
        emulatorView.setMaintainAspectRatio(maintain);
        window.onDisplaySettingsChanged(true);
    }

    private void applyScanlinesToggle() {
        setScanlines(scanlinesToggle.isSelected());
    }

    private void applySmoothToggle() {
        setSmoothScaling(smoothToggle.isSelected());
    }

    private void applyAspectToggle() {
        setMaintainAspect(aspectToggle.isSelected());
    }

    private void setColorFilter(ColorFilter filter) {
        emulatorView.setColorFilter(filter);
        window.onColorFilterChanged(filter, true);
    }

    private void selectSpeed(float speed) {
        for (SpeedOption option : SPEED_OPTIONS) {
            if (Math.abs(option.multiplier - speed) < 0.01f) {
                speedCombo.setSelectedItem(option);
                return;
            }
        }
    }

    private void openSettings() {
        if (settingsDialog != null) {
            settingsDialog.showDialog();
        }
    }

    private static final class SpeedOption {
        private final String label;
        private final float multiplier;

        private SpeedOption(String label, float multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
