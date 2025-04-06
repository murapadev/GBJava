package gbc.controller;

import gbc.model.GameBoyColor;
import gbc.view.EmulatorWindow;

public class EmulatorController {
	private GameBoyColor gbc;
	private EmulatorWindow view;


	public EmulatorController(GameBoyColor gbc, EmulatorWindow view) {
		this.gbc = gbc;
		this.view = view;
	}

	public EmulatorController() {
		this.gbc = new GameBoyColor();
		this.view = new EmulatorWindow(this.gbc);

	}


	public void start() {
		// Init view
		view.update();


		// Main loop
		while (true) {
			// Handle input
			handleInput();
			// Update model
			gbc.executeCycle();
			// Update view
			view.update();

			// Update debug view

		}

	}

	public void handleInput() {
		// Procesar las entradas del usuario y actualizar el Modelo y la Vista
	}

	public void loadRom(String path) {
		// Cargar el archivo ROM en el Modelo
		gbc.insertCartridge(path);
	}
}
