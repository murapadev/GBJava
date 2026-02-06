package gbc.blargg;

import java.io.IOException;
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
import gbc.model.memory.Memory;

/**
 * Test harness for Blargg's Game Boy test ROMs.
 * <p>
 * Blargg tests output results via serial port (0xFF01/0xFF02).
 * They write each character to SB (0xFF01), then write 0x81 to SC (0xFF02)
 * to trigger a transfer. The test captures this serial output and checks
 * for "Passed" or "Failed" strings.
 * <p>
 * Tests also display results on the LCD, but serial output is easier to
 * capture programmatically.
 */
class BlarggTest {

    private static final long MAX_CYCLES = 200_000_000L;
    private static final long MAX_DURATION_MS = 30_000L;

    @ParameterizedTest(name = "cpu_instrs/{0}")
    @MethodSource("cpuInstrsRoms")
    void cpuInstrs(String displayName, Path romPath) throws Exception {
        runBlarggTest(displayName, romPath);
    }

    @ParameterizedTest(name = "instr_timing/{0}")
    @MethodSource("instrTimingRoms")
    void instrTiming(String displayName, Path romPath) throws Exception {
        runBlarggTest(displayName, romPath);
    }

    @ParameterizedTest(name = "mem_timing/{0}")
    @MethodSource("memTimingRoms")
    void memTiming(String displayName, Path romPath) throws Exception {
        runBlarggTest(displayName, romPath);
    }

    @ParameterizedTest(name = "mem_timing-2/{0}")
    @MethodSource("memTiming2Roms")
    void memTiming2(String displayName, Path romPath) throws Exception {
        runBlarggTest(displayName, romPath);
    }

    @ParameterizedTest(name = "halt_bug/{0}")
    @MethodSource("haltBugRoms")
    void haltBug(String displayName, Path romPath) throws Exception {
        runBlarggTest(displayName, romPath);
    }

    @ParameterizedTest(name = "interrupt_time/{0}")
    @MethodSource("interruptTimeRoms")
    void interruptTime(String displayName, Path romPath) throws Exception {
        runBlarggTest(displayName, romPath);
    }

    // --- ROM providers ---

    static Stream<Arguments> cpuInstrsRoms() {
        return romsFromPath("cpu_instrs/individual");
    }

    static Stream<Arguments> instrTimingRoms() {
        return romsFromSingleFile("instr_timing/instr_timing.gb");
    }

    static Stream<Arguments> memTimingRoms() {
        return romsFromPath("mem_timing/individual");
    }

    static Stream<Arguments> memTiming2Roms() {
        return romsFromPath("mem_timing-2/rom_singles");
    }

    static Stream<Arguments> haltBugRoms() {
        return romsFromSingleFile("halt_bug.gb");
    }

    static Stream<Arguments> interruptTimeRoms() {
        return romsFromSingleFile("interrupt_time/interrupt_time.gb");
    }

    private static Stream<Arguments> romsFromSingleFile(String relativePath) {
        Path blarggRoot = locateBlarggRoot();
        Path romFile = blarggRoot.resolve(relativePath);
        if (!Files.exists(romFile)) {
            return Stream.of(Arguments.of("SKIPPED-" + relativePath, Paths.get("missing")));
        }
        String name = romFile.getFileName().toString();
        return Stream.of(Arguments.of(name, romFile));
    }

    private static Stream<Arguments> romsFromPath(String dir) {
        Path blarggRoot = locateBlarggRoot();
        Path romDir = blarggRoot.resolve(dir);

        if (!Files.isDirectory(romDir)) {
            return Stream.of(Arguments.of("SKIPPED-" + dir, Paths.get("missing")));
        }

        try {
            List<Path> roms = Files.walk(romDir, 1)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".gb"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            if (roms.isEmpty()) {
                return Stream.of(Arguments.of("SKIPPED-" + dir, Paths.get("missing")));
            }
            return roms.stream()
                    .map(p -> Arguments.of(p.getFileName().toString(), p));
        } catch (IOException e) {
            return Stream.of(Arguments.of("ERROR-" + dir, Paths.get("missing")));
        }
    }

    private static Path locateBlarggRoot() {
        Path[] candidates = {
                Paths.get("samples", "roms", "blargg", "gb-test-roms-master"),
                Paths.get("samples", "roms", "blargg"),
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p))
                return p;
        }
        return Paths.get("samples", "roms", "blargg", "gb-test-roms-master");
    }

    // --- Test execution ---

    private void runBlarggTest(String displayName, Path romPath) throws IOException {
        Assumptions.assumeTrue(Files.exists(romPath), "Missing Blargg ROM: " + romPath);

        BlarggResult result = executeBlarggRom(romPath);

        String serial = result.serialOutput();

        if (serial.contains("Passed")) {
            return;
        }

        // If no serial output, check WRAM for result text
        // Blargg tests store output text in WRAM around 0xD000+
        if (serial.isEmpty() || (!serial.contains("Passed") && !serial.contains("Failed"))) {
            String wramText = result.wramText();
            if (wramText.contains("Passed")) {
                return;
            }
            if (wramText.contains("Failed") || !serial.isEmpty()) {
                String output = serial.isEmpty() ? wramText : serial;
                Assertions.fail(String.format(
                        "Blargg test failed: %s\ncycles=%d, instructions=%d\nOutput:\n%s",
                        displayName, result.cycles(), result.instructions(), output));
            }
        }

        String message = String.format(
                "Blargg test did not pass: %s\ncycles=%d, instructions=%d\nSerial output:\n%s",
                displayName, result.cycles(), result.instructions(), serial);

        Assertions.fail(message);
    }

    private BlarggResult executeBlarggRom(Path romPath) throws IOException {
        GameBoyColor gbc = new GameBoyColor();
        gbc.getMemory().setHardwareType(HardwareType.DMG);

        Cartridge cartridge = new RomLoader().load(romPath);
        gbc.insertCartridge(cartridge);
        gbc.reset();

        Memory memory = gbc.getMemory();
        StringBuilder serialOutput = new StringBuilder();

        // Use callback to capture serial output reliably
        memory.setSerialOutputCallback(value -> {
            if (value >= 0x20 && value < 0x7F || value == '\n' || value == '\r') {
                serialOutput.append((char) value);
            }
        });

        Instant start = Instant.now();
        long cycles = 0;
        long instructions = 0;

        while (instructions < MAX_CYCLES
                && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {

            int executed = gbc.executeCycle();
            if (executed <= 0) {
                cycles += 4;
            } else {
                cycles += executed;
            }
            instructions++;

            // Check if test has completed (serial output contains result)
            String output = serialOutput.toString();
            if (output.contains("Passed") || output.contains("Failed")) {
                break;
            }
        }

        // Extract text from WRAM for tests that don't use serial
        String wramText = extractWramText(memory);

        return new BlarggResult(cycles, instructions, serialOutput.toString(), wramText);
    }

    private String extractWramText(Memory memory) {
        // Scan WRAM (0xC000-0xDFFF) for printable ASCII text containing "Passed" or
        // "Failed"
        StringBuilder sb = new StringBuilder();
        for (int addr = 0xC000; addr <= 0xDFFF; addr++) {
            int b = memory.readByte(addr) & 0xFF;
            if (b >= 0x20 && b < 0x7F) {
                sb.append((char) b);
            } else if (b == 0x0A || b == 0x0D) {
                sb.append('\n');
            } else if (sb.length() > 0 && b == 0x00) {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private record BlarggResult(long cycles, long instructions, String serialOutput, String wramText) {
    }
}
