package me.emulador.gbc.controller;

import me.emulador.gbc.model.GameBoyColor;
import me.emulador.gbc.view.EmulatorView;

public class EmulatorController {
    private GameBoyColor gbc;
    private EmulatorView view;

    public EmulatorController(GameBoyColor gbc, EmulatorView view) {
        this.gbc = gbc;
        this.view = view;
    }

    public void handleInput() {
        // Procesar las entradas del usuario y actualizar el Modelo y la Vista
    }

    public void loadRom(String path) {
        // Cargar el archivo ROM en el Modelo
    }
}
