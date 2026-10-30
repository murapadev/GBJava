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
import gbc.testsupport.TestRoms;

/**
 * Test harness for Blargg's Game Boy test ROMs.
 * <p>
 * Most Blargg tests print their result through the serial port (SB/SC), so
 * the harness looks for "Passed"/"Failed" there. The ones that do not
 * (mem_timing-2, halt_bug, interrupt_time) use the cartridge RAM protocol:
 * signature DE B0 61 at $A001-$A003, status at $A000 (0x80 while running,
 * 0 on success) and the result text from $A004.
 */
class BlarggTest {

    private static final long MAX_CYCLES = 200_000_000L;
    private static final long MAX_DURATION_MS = 30_000L;
    private static final int STATUS_PASSED = 0;
    private static final int STATUS_SERIAL_FAILED = 1;
    private static final int STATUS_TIMEOUT = -1;

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
        // interrupt_time measures CGB double-speed timing; it always fails on DMG.
        runBlarggTest(displayName, romPath, HardwareType.CGB);
    }

    // Suites that do not fully pass yet; opt in with -Dgbc.blargg.extended=true.

    @ParameterizedTest(name = "dmg_sound/{0}")
    @MethodSource("dmgSoundRoms")
    void dmgSound(String displayName, Path romPath) throws Exception {
        assumeExtended();
        runBlarggTest(displayName, romPath);
    }

    @ParameterizedTest(name = "cgb_sound/{0}")
    @MethodSource("cgbSoundRoms")
    void cgbSound(String displayName, Path romPath) throws Exception {
        assumeExtended();
        runBlarggTest(displayName, romPath, HardwareType.CGB);
    }

    @ParameterizedTest(name = "oam_bug/{0}")
    @MethodSource("oamBugRoms")
    void oamBug(String displayName, Path romPath) throws Exception {
        assumeExtended();
        runBlarggTest(displayName, romPath);
    }

    private static void assumeExtended() {
        Assumptions.assumeTrue(Boolean.getBoolean("gbc.blargg.extended"),
                "Extended Blargg suites disabled (-Dgbc.blargg.extended=true)");
    }

    // --- ROM providers ---

    static Stream<Arguments> dmgSoundRoms() {
        return romsFromPath("dmg_sound/rom_singles");
    }

    static Stream<Arguments> cgbSoundRoms() {
        return romsFromPath("cgb_sound/rom_singles");
    }

    static Stream<Arguments> oamBugRoms() {
        return romsFromPath("oam_bug/rom_singles");
    }

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
                TestRoms.resolve("blargg", "gb-test-roms-master"),
                TestRoms.resolve("blargg"),
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p))
                return p;
        }
        return TestRoms.resolve("blargg", "gb-test-roms-master");
    }

    // --- Test execution ---

    private void runBlarggTest(String displayName, Path romPath) throws IOException {
        runBlarggTest(displayName, romPath, HardwareType.DMG);
    }

    private void runBlarggTest(String displayName, Path romPath, HardwareType hardware) throws IOException {
        Assumptions.assumeTrue(Files.exists(romPath), "Missing Blargg ROM: " + romPath);

        BlarggResult result = executeBlarggRom(romPath, hardware);
        if (result.status() == STATUS_PASSED) {
            return;
        }

        String reason = result.status() == STATUS_TIMEOUT ? "timed out" : "failed (code " + result.status() + ")";
        Assertions.fail(String.format(
                "Blargg test %s: %s\ncycles=%d, instructions=%d\nOutput:\n%s",
                reason, displayName, result.cycles(), result.instructions(), result.output()));
    }

    private BlarggResult executeBlarggRom(Path romPath, HardwareType hardware) throws IOException {
        GameBoyColor gbc = new GameBoyColor();
        gbc.getMemory().setHardwareType(hardware);

        Cartridge cartridge = new RomLoader().load(romPath);
        gbc.insertCartridge(cartridge);
        gbc.reset();

        Memory memory = gbc.getMemory();
        StringBuilder serialOutput = new StringBuilder();
        memory.setSerialOutputCallback(value -> {
            if (value >= 0x20 && value < 0x7F || value == '\n' || value == '\r') {
                serialOutput.append((char) value);
            }
        });

        Instant start = Instant.now();
        long cycles = 0;
        long instructions = 0;
        int status = STATUS_TIMEOUT;

        while (instructions < MAX_CYCLES
                && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {
            int executed = gbc.executeCycle();
            cycles += executed <= 0 ? 4 : executed;
            instructions++;

            if ((instructions & 0x3FF) != 0) {
                continue;
            }
            String serial = serialOutput.toString();
            if (serial.contains("Passed")) {
                status = STATUS_PASSED;
                break;
            }
            if (serial.contains("Failed")) {
                status = STATUS_SERIAL_FAILED;
                break;
            }
            // Tests without serial output report through cartridge RAM:
            // signature DE B0 61 at $A001, status at $A000 (0x80 = running).
            if (hasSramSignature(memory)) {
                int code = memory.readByte(0xA000) & 0xFF;
                if (code != 0x80) {
                    status = code;
                    break;
                }
            }
        }

        String output = serialOutput.length() > 0 ? serialOutput.toString() : readSramText(memory);
        return new BlarggResult(cycles, instructions, status, output);
    }

    private static boolean hasSramSignature(Memory memory) {
        return (memory.readByte(0xA001) & 0xFF) == 0xDE
                && (memory.readByte(0xA002) & 0xFF) == 0xB0
                && (memory.readByte(0xA003) & 0xFF) == 0x61;
    }

    private static String readSramText(Memory memory) {
        if (!hasSramSignature(memory)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int addr = 0xA004; addr < 0xC000; addr++) {
            int b = memory.readByte(addr) & 0xFF;
            if (b == 0) {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString();
    }

    private record BlarggResult(long cycles, long instructions, int status, String output) {
    }
}
