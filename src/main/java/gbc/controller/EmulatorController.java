package main.java.controller;

import main.java.model.GameBoyColor;
import main.java.view.EmulatorView;
import main.java.view.MenuBar;

public class EmulatorController {
	private GameBoyColor gbc;
	private EmulatorView view;
	private MenuBar menuBar;

	public EmulatorController(GameBoyColor gbc, EmulatorView view) {
		this.gbc = gbc;
		this.view = view;
		this.menuBar = new MenuBar();
	}

	public EmulatorController() {
		this.gbc = new GameBoyColor();
		this.view = new EmulatorView(this.gbc.getGpu().getScreen());
	}

	public void start() {
		// Init view
		view.init();


		// Main loop
		while (true) {
			// Handle input
			handleInput();
			// Update model
			gbc.executeCycle();
			// Update view
			view.update();
			
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
