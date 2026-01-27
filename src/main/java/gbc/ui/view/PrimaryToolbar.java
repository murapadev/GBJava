package gbc.ui.view;

import gbc.ui.controller.EmulatorController;
import gbc.ui.view.EmulatorView.ColorFilter;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Objects;

/**
 * Top-level toolbar hosting quick access controls and display toggles.
 * Works in concert with {@link MenuBar} to reuse action logic while
 * remaining resilient when the rich menu is not available (fallback builds).
 */
public class PrimaryToolbar extends JToolBar {

    private final EmulatorWindow window;
    private final MenuBar menuBar;
    private final EmulatorView emulatorView;

    private final JToggleButton pauseToggle;
    private final JButton stepButton;
    private final JComboBox<SpeedOption> speedCombo;
    private final JComboBox<ColorFilter> filterCombo;
    private final JSpinner scaleSpinner;
    private final JToggleButton scanlinesToggle;
    private final JToggleButton smoothToggle;
    private final JToggleButton aspectToggle;

    private boolean updatingControls;
    private EmulatorController controller;

    private static final int CONTROL_HEIGHT = 28;
    private static final SpeedOption[] SPEED_OPTIONS = {
            new SpeedOption("25%", 0.25f),
            new SpeedOption("50%", 0.5f),
            new SpeedOption("100%", 1.0f),
            new SpeedOption("200%", 2.0f),
            new SpeedOption("400%", 4.0f)
    };

    public PrimaryToolbar(EmulatorWindow window, MenuBar menuBar, EmulatorView emulatorView) {
        super("Emulator Toolbar");
        this.window = Objects.requireNonNull(window, "window");
        this.menuBar = menuBar;
        this.emulatorView = Objects.requireNonNull(emulatorView, "emulatorView");

        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        addButton("Open", "Open ROM (Ctrl+O)", this::openRom);
        pauseToggle = addToggle("Pause", "Pause or resume emulation");
        pauseToggle.addActionListener(e -> {
            if (!updatingControls) {
                togglePause();
            }
        });
        stepButton = addButton("Step", "Execute one CPU instruction", this::stepInstruction);
        addButton("Reset", "Reset emulator (Ctrl+R)", this::resetEmulator);

        addSeparator();
        addButton("Screenshot", "Capture current frame", this::takeScreenshot);
        addButton("Debug", "Open debug view (F12)", window::openDebugView);
        addButton("VRAM", "Open VRAM viewer (F5)", window::openVRAMViewer);
        addButton("Fullscreen", "Toggle fullscreen (F11)", this::toggleFullscreen);

        addSeparator();
        speedCombo = new JComboBox<>(SPEED_OPTIONS);
        configureCombo(speedCombo, e -> {
            if (!updatingControls) {
                SpeedOption option = (SpeedOption) speedCombo.getSelectedItem();
                if (option != null) {
                    setSpeed(option.multiplier);
                }
            }
        });
        addLabeledComponent("Speed", speedCombo);

        filterCombo = new JComboBox<>(ColorFilter.values());
        configureCombo(filterCombo, e -> {
            if (!updatingControls) {
                ColorFilter filter = (ColorFilter) filterCombo.getSelectedItem();
                if (filter != null) {
                    setColorFilter(filter);
                }
            }
        });
        addLabeledComponent("Filter", filterCombo);

        scaleSpinner = new JSpinner(new SpinnerNumberModel(emulatorView.getScaleFactor(), 1, 8, 1));
        configureSpinner(scaleSpinner, e -> {
            if (!updatingControls) {
                setScale((Integer) scaleSpinner.getValue());
            }
        });
        addLabeledComponent("Scale", scaleSpinner);

        addSeparator();
        scanlinesToggle = addToggle("Scanlines", "Toggle scanlines overlay");
        scanlinesToggle.addActionListener(e -> {
            if (!updatingControls) {
                setScanlines(scanlinesToggle.isSelected());
            }
        });

        smoothToggle = addToggle("Smooth", "Toggle smooth scaling");
        smoothToggle.addActionListener(e -> {
            if (!updatingControls) {
                setSmoothScaling(smoothToggle.isSelected());
            }
        });

        aspectToggle = addToggle("Aspect", "Maintain aspect ratio");
        aspectToggle.addActionListener(e -> {
            if (!updatingControls) {
                setMaintainAspect(aspectToggle.isSelected());
            }
        });
        addButton("Settings", "Graphics options dialog", this::openGraphicsOptions);
    }

    public void setController(EmulatorController controller) {
        this.controller = controller;
    }

    public void syncState(boolean paused, float speed, ColorFilter filter,
            boolean scanlines, boolean smooth, boolean maintainAspect, int scale) {
        updatingControls = true;
        try {
            pauseToggle.setSelected(paused);
            pauseToggle.setText(paused ? "Resume" : "Pause");
            stepButton.setEnabled(paused || controller == null);

            selectSpeed(speed);
            filterCombo.setSelectedItem(filter);
            scanlinesToggle.setSelected(scanlines);
            smoothToggle.setSelected(smooth);
            aspectToggle.setSelected(maintainAspect);
            scaleSpinner.setValue(Math.max(1, Math.min(8, scale)));
        } finally {
            updatingControls = false;
        }
    }

    private JButton addButton(String text, String tooltip, Runnable action) {
        JButton button = new JButton(text);
        configureAbstractButton(button, tooltip);
        button.addActionListener(e -> {
            if (!updatingControls) {
                action.run();
            }
        });
        add(button);
        return button;
    }

    private JToggleButton addToggle(String text, String tooltip) {
        JToggleButton toggle = new JToggleButton(text);
        configureAbstractButton(toggle, tooltip);
        add(toggle);
        return toggle;
    }

    private void configureAbstractButton(AbstractButton button, String tooltip) {
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 10, 2, 10));
        button.setToolTipText(tooltip);
    }

    private void configureCombo(JComboBox<?> combo, java.awt.event.ActionListener listener) {
        combo.setMaximumSize(new Dimension(140, CONTROL_HEIGHT));
        combo.addActionListener(listener);
    }

    private void configureSpinner(JSpinner spinner, ChangeListener listener) {
        spinner.setMaximumSize(new Dimension(70, CONTROL_HEIGHT));
        spinner.addChangeListener(listener);
    }

    private void addLabeledComponent(String label, JComponent component) {
        JLabel jLabel = new JLabel(label);
        jLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        add(jLabel);
        add(component);
    }

    private void openRom() {
        if (menuBar != null) {
            menuBar.promptOpenRomDialog();
        } else {
            window.showMessage("Menu is unavailable in this build. Use the CLI to load a ROM.",
                    "Open ROM", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void togglePause() {
        if (menuBar != null) {
            menuBar.togglePauseFromToolbar();
        } else {
            window.togglePause();
        }
    }

    private void stepInstruction() {
        window.stepInstruction();
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
            window.setStatusText("Screenshot captured");
        }
    }

    private void toggleFullscreen() {
        if (menuBar != null) {
            menuBar.toggleFullscreenFromToolbar();
        } else {
            window.toggleFullscreen();
        }
    }

    private void openGraphicsOptions() {
        if (menuBar != null) {
            menuBar.showGraphicsOptionsFromToolbar();
        } else {
            window.showMessage("Graphics options dialog is unavailable in minimal mode.",
                    "Graphics Options", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void setSpeed(float speed) {
        if (menuBar != null) {
            menuBar.setEmulationSpeedFromToolbar(speed);
        } else if (controller != null) {
            controller.getGameBoyColor().setSpeedMultiplier(speed);
            window.onSpeedChanged(speed);
        } else {
            window.getGameBoyColor().setSpeedMultiplier(speed);
            window.onSpeedChanged(speed);
        }
    }

    private void setScale(int scale) {
        if (menuBar != null) {
            menuBar.setScaleFromToolbar(scale);
        } else {
            emulatorView.setScaleFactor(scale);
            window.setStatusText("Scale: " + scale + "x");
            window.refreshUiState(true);
        }
    }

    private void setScanlines(boolean enabled) {
        if (menuBar != null) {
            menuBar.setScanlinesFromToolbar(enabled);
        } else {
            emulatorView.setShowScanlines(enabled);
            window.onDisplaySettingsChanged(true);
            window.setStatusText(enabled ? "Scanlines enabled" : "Scanlines disabled");
        }
    }

    private void setSmoothScaling(boolean enabled) {
        if (menuBar != null) {
            menuBar.setSmoothScalingFromToolbar(enabled);
        } else {
            emulatorView.setSmoothScaling(enabled);
            window.onDisplaySettingsChanged(true);
            window.setStatusText(enabled ? "Smooth scaling enabled" : "Smooth scaling disabled");
        }
    }

    private void setMaintainAspect(boolean enabled) {
        if (menuBar != null) {
            menuBar.setMaintainAspectFromToolbar(enabled);
        } else {
            emulatorView.setMaintainAspectRatio(enabled);
            window.onDisplaySettingsChanged(true);
            window.setStatusText(enabled ? "Aspect ratio locked" : "Aspect ratio free");
        }
    }

    private void setColorFilter(ColorFilter filter) {
        if (menuBar != null) {
            menuBar.setColorFilterFromToolbar(filter);
        } else {
            emulatorView.setColorFilter(filter);
            window.onColorFilterChanged(filter, true);
            window.setStatusText("Filter: " + filter);
        }
    }

    private void selectSpeed(float speed) {
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (Float.compare(SPEED_OPTIONS[i].multiplier, speed) == 0) {
                speedCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static final class SpeedOption {
        final String label;
        final float multiplier;

        SpeedOption(String label, float multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
