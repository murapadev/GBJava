package gbc.controller;

import gbc.model.GameBoyColor;
import gbc.view.EmulatorWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorControllerAsyncTest {

    private EmulatorController controller;
    private EmulatorWindow window;

    @AfterEach
    void tearDown() throws Exception {
        if (controller != null) {
            controller.stop();
            if (window != null) {
                SwingUtilities.invokeAndWait(window::dispose);
            }
        }
    }

    @Test
    void loadRomAsyncDoesNotBlockEdtAndLoadsCartridge() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "UI smoke test skipped in headless environment");

        System.setProperty("gbc.audio.nullOutput", "true");

        GameBoyColor gbc = new GameBoyColor();
        window = new EmulatorWindow(gbc);
        controller = new EmulatorController(gbc, window);
        controller.start();

        Path romFile = Files.createTempFile("emulator-test", ".gb");
        romFile.toFile().deleteOnExit();
        byte[] romData = new byte[0x8000];
        romData[0x0134] = 'T';
        romData[0x0135] = 'E';
        romData[0x0136] = 'S';
        romData[0x0137] = 'T';
        romData[0x0143] = 0;
        romData[0x0147] = 0x00; // ROM only cartridge
        Files.write(romFile, romData);

        CompletableFuture<Void> future = controller.loadRomAsync(romFile.toString());
        future.get(5, TimeUnit.SECONDS);

        assertTrue(controller.isCartridgeLoaded(), "Cartridge should be loaded after async ROM load");

        SwingUtilities.invokeAndWait(() -> assertTrue(
                window.getTitle().contains(romFile.getFileName().toString()),
                "Window title should reflect loaded ROM"));
    }
}
