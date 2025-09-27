package gbc.mooneye;

import gbc.model.GameBoyColor;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

class MooneyeAcceptanceTest {

    private static final long MAX_CYCLES = 50_000_000L;
    private static final long MAX_DURATION_MS = 5_000L;

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptanceRoms")
    void mooneyeAcceptanceShouldReachPassState(String displayName, Path romPath) throws Exception {
        Assumptions.assumeTrue(Files.exists(romPath), "Missing Mooneye ROM: " + romPath);

        MooneyeResult result = runMooneyeTest(romPath);
        Assertions.assertTrue(result.passed(), () -> failureMessage(displayName, romPath, result));
    }

    static Stream<Arguments> acceptanceRoms() {
        Path mooneyeRoot = locateMooneyeRoot();
        Path acceptanceRoot = mooneyeRoot.resolve("acceptance");
        Assumptions.assumeTrue(Files.isDirectory(acceptanceRoot),
                "Mooneye acceptance directory not found: " + acceptanceRoot);

        String filter = System.getProperty("mooneye.only");

        try {
            return Files.walk(acceptanceRoot)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".gb"))
                    .sorted(Comparator.comparing(path -> acceptanceRoot.relativize(path).toString()))
                    .filter(path -> filter == null
                            || acceptanceRoot.relativize(path).toString().contains(filter))
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
        throw new IllegalStateException("Unable to locate mooneye-test-suite directory relative to core module");
    }

    private MooneyeResult runMooneyeTest(Path romPath) throws IOException {
        GameBoyColor gbc = new GameBoyColor();
        gbc.insertCartridge(romPath.toString());

        Registers registers = gbc.getCpu().getRegisters();
        Memory memory = gbc.getMemory();
        Instant start = Instant.now();
        long cycles = 0;
        long instructions = 0;

        while (cycles < MAX_CYCLES && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {
            int executed = gbc.executeCycle();
            if (executed <= 0) {
                continue;
            }
            cycles += executed;
            instructions++;

            if (isPassState(registers)) {
                return MooneyeResult.passed(cycles, instructions, registers, snapshotDebugState(memory, gbc));
            }
        }

        return MooneyeResult.failed(cycles, instructions, registers, snapshotDebugState(memory, gbc));
    }

    private boolean isPassState(Registers registers) {
        return (registers.getRegister("B") & 0xFF) == 3
                && (registers.getRegister("C") & 0xFF) == 5
                && (registers.getRegister("D") & 0xFF) == 8
                && (registers.getRegister("E") & 0xFF) == 13
                && (registers.getRegister("H") & 0xFF) == 21
                && (registers.getRegister("L") & 0xFF) == 34;
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
