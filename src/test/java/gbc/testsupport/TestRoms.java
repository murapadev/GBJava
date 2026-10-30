package gbc.testsupport;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Root directory for external test ROMs (blargg, mooneye, acid2).
 * <p>
 * Defaults to {@code samples/roms}; override with {@code -Dgbc.testRoms=/path}
 * to run the suites against ROMs kept outside the repository.
 */
public final class TestRoms {

    private TestRoms() {
    }

    public static Path root() {
        String override = System.getProperty("gbc.testRoms");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("samples", "roms");
    }

    public static Path resolve(String first, String... more) {
        return root().resolve(Paths.get(first, more));
    }
}
