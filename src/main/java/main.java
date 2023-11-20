package me.emulador.gbc;
import main.java.controller.EmulatorController;


public class main {

	public static void main() {
		EmulatorController controller = new EmulatorController();
		controller.loadRom("./Tetris.gb");
		controller.start();
	}

	
}
