package gbc.view;

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
        switch (theme == null ? "" : theme.toLowerCase(Locale.ROOT)) {
            case "dark" -> FlatDarkLaf.setup();
            case "light", "" -> FlatLightLaf.setup();
            default -> {
                // Fallback to light if unsupported value
                FlatLightLaf.setup();
            }
        }
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 10);
    }
}
