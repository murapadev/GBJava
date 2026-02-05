package gbc.controller.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gbc.model.cartridge.Cartridge;
import gbc.model.cartridge.CartridgeFactory;

/**
 * Loads ROM files into cartridge instances.
 */
public final class RomLoader {
    private static final int MIN_ROM_HEADER_SIZE = 0x150;

    public Cartridge load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }

        byte[] data;
        Path romPath = path;

        if (isZip(path)) {
            ZipRom zipRom = readZipRom(path);
            data = zipRom.data;
            romPath = zipRom.romPath;
        } else {
            data = Files.readAllBytes(path);
        }

        if (data.length < MIN_ROM_HEADER_SIZE) {
            throw new IOException("ROM file too small: " + data.length + " bytes");
        }

        Cartridge cartridge = CartridgeFactory.create(data);
        cartridge.setRomPath(romPath.toString());
        return cartridge;
    }

    private static boolean isZip(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }

    private static ZipRom readZipRom(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry candidate = null;
            boolean candidateIsGbc = false;

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                boolean isGb = name.endsWith(".gb");
                boolean isGbc = name.endsWith(".gbc");
                if (!isGb && !isGbc) {
                    continue;
                }
                if (candidate == null || (isGbc && !candidateIsGbc)) {
                    candidate = entry;
                    candidateIsGbc = isGbc;
                }
            }

            if (candidate == null) {
                throw new IOException("No .gb/.gbc entry found in " + path.getFileName());
            }

            byte[] data;
            try (InputStream in = zip.getInputStream(candidate)) {
                data = in.readAllBytes();
            }

            Path romPath = buildVirtualRomPath(path, candidate.getName());
            return new ZipRom(data, romPath);
        }
    }

    private static Path buildVirtualRomPath(Path archivePath, String entryName) {
        String fileName = Path.of(entryName).getFileName().toString();
        Path parent = archivePath.getParent();
        return parent != null ? parent.resolve(fileName) : Path.of(fileName);
    }

    private static final class ZipRom {
        private final byte[] data;
        private final Path romPath;

        private ZipRom(byte[] data, Path romPath) {
            this.data = data;
            this.romPath = romPath;
        }
    }
}
