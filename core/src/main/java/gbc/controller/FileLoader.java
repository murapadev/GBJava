package gbc.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileLoader {
    public static byte[] loadFile(String path) {
        File file = new File(path);
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}