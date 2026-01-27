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

import gbc.model.GameBoyColor;
import gbc.model.HardwareType;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;

class MooneyeAcceptanceTest {

    private static final long MAX_CYCLES = 100_000_000L;
    private static final long MAX_DURATION_MS = 10_000L;

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptanceRoms")
    void root(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bitsRoms")
    void bits(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instrRoms")
    void instr(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("interruptsRoms")
    void interrupts(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oamDmaRoms")
    void oamDma(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ppuRoms")
    void ppu(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serialRoms")
    void serial(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timerRoms")
    void timer(String displayName, Path romPath) throws Exception {
        runTest(displayName, romPath);
    }

    private void runTest(String displayName, Path romPath) throws IOException {
        Assumptions.assumeTrue(Files.exists(romPath), "Missing Mooneye ROM: " + romPath);

        MooneyeResult result = runMooneyeTest(romPath);
        Assertions.assertTrue(result.passed(), () -> failureMessage(displayName, romPath, result));
    }

    static Stream<Arguments> acceptanceRoms() {
        return romsFromDirectory(null, false);
    }

    static Stream<Arguments> bitsRoms() {
        return romsFromDirectory("bits", true);
    }

    static Stream<Arguments> instrRoms() {
        return romsFromDirectory("instr", true);
    }

    static Stream<Arguments> interruptsRoms() {
        return romsFromDirectory("interrupts", true);
    }

    static Stream<Arguments> oamDmaRoms() {
        return romsFromDirectory("oam_dma", true);
    }

    static Stream<Arguments> ppuRoms() {
        return romsFromDirectory("ppu", true);
    }

    static Stream<Arguments> serialRoms() {
        return romsFromDirectory("serial", true);
    }

    static Stream<Arguments> timerRoms() {
        return romsFromDirectory("timer", true);
    }

    private static Stream<Arguments> romsFromDirectory(String dir, boolean recursive) {
        Path mooneyeRoot = locateMooneyeRoot();
        Path acceptanceRoot = mooneyeRoot.resolve("acceptance");
        Path romDir = (dir == null) ? acceptanceRoot : acceptanceRoot.resolve(dir);

        Assumptions.assumeTrue(Files.isDirectory(romDir),
                "Mooneye acceptance directory not found: " + romDir);

        String filter = System.getProperty("mooneye.only");

        try {
            int maxDepth = recursive ? Integer.MAX_VALUE : 1;
            Stream<Path> roms = Files.walk(romDir, maxDepth)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".gb"));

            if (filter != null) {
                roms = roms.filter(path -> acceptanceRoot.relativize(path).toString().contains(filter));
            }

            List<Path> romList = roms.sorted(Comparator.comparing(path -> romDir.relativize(path).toString()))
                    .toList();
            if (romList.isEmpty()) {
                return Stream.of(Arguments.of("SKIPPED-" + (dir == null ? "root" : dir),
                        Paths.get("missing")));
            }
            return romList.stream()
                    .map(path -> Arguments.of(acceptanceRoot.relativize(path).toString(), path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path locateMooneyeRoot() {
        Path candidate = Paths.get("..", "mooneye-test-suite");
        if (Files.isDirectory(candidate)) {
            return candidate.normalize();
        }
        Path alternative = Paths.get("mooneye-test-suite");
        if (Files.isDirectory(alternative)) {
            return alternative.normalize();
        }
        Path samplesPath = Paths.get("samples", "roms", "mooneye-test-suite");
        if (Files.isDirectory(samplesPath)) {
            return samplesPath.normalize();
        }
        throw new IllegalStateException("Unable to locate mooneye-test-suite directory relative to core module");
    }

    private MooneyeResult runMooneyeTest(Path romPath) throws IOException {
        GameBoyColor gbc = new GameBoyColor();
        
        // Detect hardware type from ROM name and configure before loading
        String romName = romPath.getFileName().toString();
        HardwareType hwType = HardwareType.fromRomName(romName);
        gbc.getMemory().setHardwareType(hwType);
        
        gbc.insertCartridge(romPath.toString());
        
        // Reset to apply hardware-specific initial values
        gbc.reset();

        Registers registers = gbc.getCpu().getRegisters();
        Memory memory = gbc.getMemory();
        Instant start = Instant.now();
        long cycles = 0;
        long instructions = 0;

        try {
            while (instructions < MAX_CYCLES && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {
                int executed = gbc.executeCycle();
                if (executed <= 0) {
                    // If no cycles were executed (e.g., paused or halted without interrupts),
                    // we still need to ensure the loop can terminate.
                    // For testing, we can advance a minimal number of cycles.
                    cycles += 4; // Advance by one machine cycle
                } else {
                    cycles += executed;
                }
                instructions++;

                if (isPassState(registers)) {
                    return MooneyeResult.passed(cycles, instructions, registers, snapshotDebugState(memory, gbc));
                }

                // Check for failure state
                if (isFailureState(registers)) {
                    return MooneyeResult.failed(cycles, instructions, registers, snapshotDebugState(memory, gbc));
                }
            }

            return MooneyeResult.failed(cycles, instructions, registers, snapshotDebugState(memory, gbc));
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

    private String failureMessage(String displayName, Path romPath, MooneyeResult result) {
        String registersDump = String.format(
                "B=%02X C=%02X D=%02X E=%02X H=%02X L=%02X PC=%04X",
                result.registers().getRegister("B") & 0xFF,
                result.registers().getRegister("C") & 0xFF,
                result.registers().getRegister("D") & 0xFF,
                result.registers().getRegister("E") & 0xFF,
                result.registers().getRegister("H") & 0xFF,
                result.registers().getRegister("L") & 0xFF,
                result.pc());

        StringBuilder sb = new StringBuilder();
        sb.append("Mooneye test did not reach pass state: ")
                .append(displayName)
                .append(" (")
                .append(romPath)
                .append(")\n")
                .append("cycles=")
                .append(result.cycles())
                .append(", instructions=")
                .append(result.instructions())
                .append("\n")
                .append(registersDump);

        if (result.debugState() != null) {
            DebugState debug = result.debugState();
            sb.append("\nDIV=")
                    .append(String.format("%02X", debug.div()))
                    .append(" TIMA=")
                    .append(String.format("%02X", debug.tima()))
                    .append(" TMA=")
                    .append(String.format("%02X", debug.tma()))
                    .append(" TAC=")
                    .append(String.format("%02X", debug.tac()))
                    .append(" IF=")
                    .append(String.format("%02X", debug.interruptFlags()))
                    .append(" IE=")
                    .append(String.format("%02X", debug.interruptEnable()))
                    .append(" IME=")
                    .append(debug.ime() ? 1 : 0)
                    .append(" HALT=")
                    .append(debug.halted() ? 1 : 0)
                    .append(" LCDC=")
                    .append(String.format("%02X", debug.lcdc()))
                    .append(" STAT=")
                    .append(String.format("%02X", debug.stat()))
                    .append(" LY=")
                    .append(String.format("%02X", debug.ly()))
                    .append(" LYC=")
                    .append(String.format("%02X", debug.lyc()));
        }

        return sb.toString();
    }

    private record MooneyeResult(boolean passed, long cycles, long instructions, Registers registers, int pc,
            DebugState debugState) {
        static MooneyeResult passed(long cycles, long instructions, Registers registers, DebugState debugState) {
            return new MooneyeResult(true, cycles, instructions, snapshot(registers), registers.getPC(), debugState);
        }

        static MooneyeResult failed(long cycles, long instructions, Registers registers, DebugState debugState) {
            return new MooneyeResult(false, cycles, instructions, snapshot(registers), registers.getPC(), debugState);
        }

        private static Registers snapshot(Registers source) {
            return source.copy();
        }
    }

    private DebugState snapshotDebugState(Memory memory, GameBoyColor gbc) {
        try {
            int div = memory.readByte(0xFF04) & 0xFF;
            int tima = memory.readByte(0xFF05) & 0xFF;
            int tma = memory.readByte(0xFF06) & 0xFF;
            int tac = memory.readByte(0xFF07) & 0xFF;
            int interruptFlags = memory.readByte(0xFF0F) & 0xFF;
            int interruptEnable = memory.readByte(0xFFFF) & 0xFF;
            boolean ime = gbc.getCpu().isIme();
            boolean halted = gbc.getCpu().isHalted();

            int lcdc = memory.readByte(0xFF40) & 0xFF;
            int stat = memory.readByte(0xFF41) & 0xFF;
            int ly = memory.readByte(0xFF44) & 0xFF;
            int lyc = memory.readByte(0xFF45) & 0xFF;

            return new DebugState(div, tima, tma, tac, interruptFlags, interruptEnable, ime, halted, lcdc, stat, ly,
                    lyc);
        } catch (Exception e) {
            return null;
        }
    }

    private record DebugState(int div, int tima, int tma, int tac, int interruptFlags, int interruptEnable,
            boolean ime, boolean halted, int lcdc, int stat, int ly, int lyc) {
    }
}
