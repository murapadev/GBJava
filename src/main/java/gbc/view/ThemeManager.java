package gbc.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Locale;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import gbc.controller.config.AppConfig;

public final class ThemeManager {
    private ThemeManager() {
    }

    public static void apply() {
        String theme = AppConfig.get().getStringLower("ui.theme", "light");
        boolean dark;
        switch (theme == null ? "" : theme.toLowerCase(Locale.ROOT)) {
            case "dark" -> { FlatDarkLaf.setup(); dark = true; }
            case "light", "" -> { FlatLightLaf.setup(); dark = false; }
            default -> {
                FlatLightLaf.setup();
                dark = false;
            }
        }

        // Component rounding
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 10);

        // Scrollbar styling
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.showButtons", false);

        // Tab styling
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabHeight", 32);
        UIManager.put("TabbedPane.selectedBackground",
                dark ? new Color(60, 63, 65) : new Color(230, 235, 240));

        // Menu item selection
        UIManager.put("MenuItem.selectionArc", 6);

        // Focus ring
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);

        // Tooltip
        UIManager.put("ToolTip.background",
                dark ? new Color(50, 50, 55) : new Color(255, 255, 225));

        // Table
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));

        // Separator
        UIManager.put("Separator.stripeWidth", 1);
    }
}
