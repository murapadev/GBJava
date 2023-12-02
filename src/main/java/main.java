import gbc.controller.EmulatorController;


public class main {

	public static void main(String[] args) {
		EmulatorController controller = new EmulatorController();
		controller.loadRom("./Tetris.gb");
		controller.start();
	}

	
}
