package com.emulador.gbc.controller;

import com.emulador.gbc.model.GameBoyColor;
import com.emulador.gbc.view.EmulatorView;

public class EmulatorController {
    private GameBoyColor gbc;
    private EmulatorView view;

    public EmulatorController(GameBoyColor gbc, EmulatorView view) {
        this.gbc = gbc;
        this.view = view;
    }

    public void handleInput(Input input) {
        // Procesar las entradas del usuario y actualizar el Modelo y la Vista
    }
}
