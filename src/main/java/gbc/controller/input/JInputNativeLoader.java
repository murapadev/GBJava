package gbc.controller.input;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class JInputNativeLoader {
    private static volatile boolean loaded;

    private JInputNativeLoader() {}

    public static synchronized void loadIfNeeded() {
        if (loaded) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String resource = null;
        if (os.contains("linux")) {
            resource = arch.contains("64") ? "/libjinput-linux64.so" : "/libjinput-linux.so";
        } else if (os.contains("windows")) {
            resource = arch.contains("64") ? "/jinput-raw_64.dll" : "/jinput-raw.dll";
        } else if (os.contains("mac")) {
            resource = "/libjinput-osx.jnilib";
        }

        if (resource == null) {
            return;
        }

        try (InputStream in = JInputNativeLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            Path dir = Path.of("target", "natives");
            Files.createDirectories(dir);
            Path out = dir.resolve(resource.substring(1));
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            System.load(out.toAbsolutePath().toString());
            String nativeDir = dir.toAbsolutePath().toString();
            System.setProperty("net.java.games.input.librarypath", nativeDir);
            String current = System.getProperty("java.library.path", "");
            if (!current.contains(nativeDir)) {
                System.setProperty("java.library.path", current + java.io.File.pathSeparator + nativeDir);
                try {
                    java.lang.reflect.Field sysPaths = ClassLoader.class.getDeclaredField("sys_paths");
                    sysPaths.setAccessible(true);
                    sysPaths.set(null, null);
                } catch (Exception ignored) {
                }
            }
            loaded = true;
        } catch (IOException | UnsatisfiedLinkError ignored) {
        }
    }
}
