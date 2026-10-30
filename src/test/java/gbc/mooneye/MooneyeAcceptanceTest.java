package gbc.mooneye;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import gbc.controller.io.RomLoader;
import gbc.model.GameBoyColor;
import gbc.model.HardwareType;
import gbc.model.cpu.Registers;
import gbc.testsupport.TestRoms;

/**
 * Runs every DMG-compatible ROM of the mooneye acceptance suite.
 * <p>
 * Not all of them pass yet, so the suite is opt-in:
 * {@code -Dgbc.mooneye.acceptance=true}. The stable boot subset lives in
 * {@link MooneyeBootCompatibilityTest}.
 */
class MooneyeAcceptanceTest {

    private static final long MAX_INSTRUCTIONS = 20_000_000L;
    private static final long MAX_DURATION_MS = 10_000L;
    // Mooneye hardware suffixes that include DMG-ABC (G = DMG+MGB, A = all models).
    private static final Set<String> DMG_SUFFIXES = Set.of("dmgABC", "dmgABCmgb", "G", "GS", "A");

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptanceRoms")
    void acceptance(String name, Path romPath) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("gbc.mooneye.acceptance"),
                "Mooneye acceptance suite disabled (-Dgbc.mooneye.acceptance=true)");
        Assumptions.assumeTrue(Files.exists(romPath), "Missing ROM: " + romPath);

        GameBoyColor gbc = new GameBoyColor();
        gbc.getMemory().setHardwareType(HardwareType.DMG);
        gbc.insertCartridge(new RomLoader().load(romPath));
        gbc.reset();

        Registers registers = gbc.getCpu().getRegisters();
        Instant start = Instant.now();
        long instructions = 0;
        try {
            while (instructions++ < MAX_INSTRUCTIONS
                    && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {
                gbc.executeCycle();
                if (isFibonacci(registers)) {
                    return;
                }
                if (isFailure(registers)) {
                    break;
                }
            }
            Assertions.fail(String.format("%s did not pass: B=%02X C=%02X D=%02X E=%02X H=%02X L=%02X PC=%04X",
                    name,
                    registers.getRegister("B") & 0xFF, registers.getRegister("C") & 0xFF,
                    registers.getRegister("D") & 0xFF, registers.getRegister("E") & 0xFF,
                    registers.getRegister("H") & 0xFF, registers.getRegister("L") & 0xFF,
                    registers.getPC() & 0xFFFF));
        } finally {
            gbc.closeTrace();
        }
    }

    static Stream<Arguments> acceptanceRoms() {
        Path root = TestRoms.resolve("mooneye-test-suite", "acceptance");
        if (!Files.isDirectory(root)) {
            return Stream.of(Arguments.of("SKIPPED-acceptance", root.resolve("missing")));
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".gb"))
                    .filter(MooneyeAcceptanceTest::runsOnDmg)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(p -> Arguments.of(root.relativize(p).toString(), p))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean runsOnDmg(Path rom) {
        String base = rom.getFileName().toString().replaceFirst("\\.gb$", "");
        int dash = base.lastIndexOf('-');
        return dash < 0 || DMG_SUFFIXES.contains(base.substring(dash + 1));
    }

    private static boolean isFibonacci(Registers r) {
        return (r.getRegister("B") & 0xFF) == 3 && (r.getRegister("C") & 0xFF) == 5
                && (r.getRegister("D") & 0xFF) == 8 && (r.getRegister("E") & 0xFF) == 13
                && (r.getRegister("H") & 0xFF) == 21 && (r.getRegister("L") & 0xFF) == 34;
    }

    private static boolean isFailure(Registers r) {
        return (r.getRegister("B") & 0xFF) == 0x42 && (r.getRegister("C") & 0xFF) == 0x42
                && (r.getRegister("D") & 0xFF) == 0x42 && (r.getRegister("E") & 0xFF) == 0x42
                && (r.getRegister("H") & 0xFF) == 0x42 && (r.getRegister("L") & 0xFF) == 0x42;
    }
}
