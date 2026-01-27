package gbc.core.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileLoader {

    private static final Logger LOGGER = Logger.getLogger(FileLoader.class.getName());

    public static byte[] loadFile(String path) {
        File file = new File(path);
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load file " + path, e);
            return null;
        }
    }
}
