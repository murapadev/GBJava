package gbc.mooneye;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import gbc.controller.io.RomLoader;
import gbc.model.GameBoyColor;
import gbc.model.HardwareType;
import gbc.model.cartridge.Cartridge;
import gbc.model.cpu.Registers;

/**
 * Test harness for Mooneye emulator-only MBC (Memory Bank Controller) tests.
 * These tests validate correct behavior of MBC1, MBC2, and MBC5 cartridge
 * mappers.
 * <p>
 * Pass/fail detection uses the same Fibonacci register convention as Mooneye
 * acceptance tests.
 */
class MooneyeMbcTest {

    private static final long MAX_CYCLES = 100_000_000L;
    private static final long MAX_DURATION_MS = 10_000L;

    @ParameterizedTest(name = "mbc1/{0}")
    @MethodSource("mbc1Roms")
    void mbc1(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "mbc2/{0}")
    @MethodSource("mbc2Roms")
    void mbc2(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "mbc5/{0}")
    @MethodSource("mbc5Roms")
    void mbc5(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    static Stream<Arguments> mbc1Roms() {
        return romsFromDirectory("mbc1");
    }

    static Stream<Arguments> mbc2Roms() {
        return romsFromDirectory("mbc2");
    }

    static Stream<Arguments> mbc5Roms() {
        return romsFromDirectory("mbc5");
    }

    private static Stream<Arguments> romsFromDirectory(String mbcDir) {
        Path mooneyeRoot = locateMooneyeRoot();
        Path romDir = mooneyeRoot.resolve("emulator-only").resolve(mbcDir);

        Assumptions.assumeTrue(Files.isDirectory(romDir),
                "Mooneye MBC directory not found: " + romDir);

        try {
            List<Path> roms = Files.walk(romDir, 1)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".gb"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            if (roms.isEmpty()) {
                return Stream.of(Arguments.of("SKIPPED-" + mbcDir, Paths.get("missing")));
            }

            return roms.stream()
                    .map(p -> Arguments.of(p.getFileName().toString(), p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path locateMooneyeRoot() {
        Path candidate = Paths.get("..", "mooneye-test-suite");
        if (Files.isDirectory(candidate))
            return candidate.normalize();
        Path alt = Paths.get("mooneye-test-suite");
        if (Files.isDirectory(alt))
            return alt.normalize();
        Path samples = Paths.get("samples", "roms", "mooneye-test-suite");
        if (Files.isDirectory(samples))
            return samples.normalize();
        throw new IllegalStateException("Cannot locate mooneye-test-suite directory");
    }

    private void runTest(String displayName, Path romPath) throws IOException {
        Assumptions.assumeTrue(Files.exists(romPath), "Missing ROM: " + romPath);

        GameBoyColor gbc = new GameBoyColor();
        gbc.getMemory().setHardwareType(HardwareType.DMG);

        Cartridge cartridge = new RomLoader().load(romPath);
        gbc.insertCartridge(cartridge);
        gbc.reset();

        Registers registers = gbc.getCpu().getRegisters();
        Instant start = Instant.now();
        long cycles = 0;
        long instructions = 0;

        try {
            while (instructions < MAX_CYCLES
                    && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {

                int executed = gbc.executeCycle();
                if (executed <= 0) {
                    cycles += 4;
                } else {
                    cycles += executed;
                }
                instructions++;

                if (isPassState(registers)) {
                    return; // Passed
                }

                if (isFailureState(registers)) {
                    break;
                }
            }

            Assertions.fail(String.format(
                    "Mooneye MBC test did not pass: %s\ncycles=%d, instructions=%d\n" +
                            "B=%02X C=%02X D=%02X E=%02X H=%02X L=%02X PC=%04X",
                    displayName, cycles, instructions,
                    registers.getRegister("B") & 0xFF,
                    registers.getRegister("C") & 0xFF,
                    registers.getRegister("D") & 0xFF,
                    registers.getRegister("E") & 0xFF,
                    registers.getRegister("H") & 0xFF,
                    registers.getRegister("L") & 0xFF,
                    registers.getPC()));
        } finally {
            gbc.closeTrace();
        }
    }

    private boolean isPassState(Registers registers) {
        return (registers.getRegister("B") & 0xFF) == 3
                && (registers.getRegister("C") & 0xFF) == 5
                && (registers.getRegister("D") & 0xFF) == 8
                && (registers.getRegister("E") & 0xFF) == 13
                && (registers.getRegister("H") & 0xFF) == 21
                && (registers.getRegister("L") & 0xFF) == 34;
    }

    private boolean isFailureState(Registers registers) {
        return (registers.getRegister("B") & 0xFF) == 0x42
                && (registers.getRegister("C") & 0xFF) == 0x42
                && (registers.getRegister("D") & 0xFF) == 0x42
                && (registers.getRegister("E") & 0xFF) == 0x42
                && (registers.getRegister("H") & 0xFF) == 0x42
                && (registers.getRegister("L") & 0xFF) == 0x42;
    }
}
