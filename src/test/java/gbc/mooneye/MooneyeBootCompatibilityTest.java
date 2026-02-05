package gbc.mooneye;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * Mooneye acceptance subset focused on post-boot compatibility.
 * These tests validate our "no boot ROM" startup profiles for DMG variants,
 * SGB variants and related HWIO/DIV register states.
 */
class MooneyeBootCompatibilityTest {

    private static final long MAX_INSTRUCTIONS = 40_000_000L;
    private static final long MAX_DURATION_MS = 10_000L;

    @ParameterizedTest(name = "{0}")
    @MethodSource("bootRoms")
    void bootCompatibility(String romName) throws Exception {
        Path romPath = locateMooneyeRoot().resolve("acceptance").resolve(romName);
        Assumptions.assumeTrue(Files.exists(romPath), "Missing Mooneye ROM: " + romPath);

        HardwareType hardwareType = HardwareType.fromRomName(romName);
        MooneyeResult result = runMooneye(romPath, hardwareType);

        Assertions.assertTrue(result.passed,
                () -> String.format(
                        "Mooneye boot compatibility test failed: %s (hw=%s) PC=%04X B=%02X C=%02X D=%02X E=%02X H=%02X L=%02X IO=%s",
                        romName,
                        hardwareType,
                        result.registers.getPC() & 0xFFFF,
                        result.registers.getRegister("B") & 0xFF,
                        result.registers.getRegister("C") & 0xFF,
                        result.registers.getRegister("D") & 0xFF,
                        result.registers.getRegister("E") & 0xFF,
                        result.registers.getRegister("H") & 0xFF,
                        result.registers.getRegister("L") & 0xFF,
                        result.ioSnapshot,
                        result.failContext.isEmpty() ? "" : (" FAIL_CTX=" + result.failContext)));
    }

    static Stream<Arguments> bootRoms() {
        // Default profile: stable subset expected to pass in CI.
        List<Arguments> roms = new ArrayList<>(List.of(
                Arguments.of("boot_div-dmgABCmgb.gb"),
                Arguments.of("boot_hwio-dmgABCmgb.gb"),
                Arguments.of("boot_regs-dmg0.gb"),
                Arguments.of("boot_regs-dmgABC.gb"),
                Arguments.of("boot_regs-mgb.gb"),
                Arguments.of("boot_regs-sgb.gb"),
                Arguments.of("boot_regs-sgb2.gb")));

        // Extended profile: enable with -Dgbc.mooneye.boot.extended=true
        // to track currently pending DMG0/SGB boot_div+boot_hwio quirks.
        if (Boolean.getBoolean("gbc.mooneye.boot.extended")) {
            roms.add(Arguments.of("boot_div-dmg0.gb"));
            roms.add(Arguments.of("boot_div-S.gb"));
            roms.add(Arguments.of("boot_div2-S.gb"));
            roms.add(Arguments.of("boot_hwio-dmg0.gb"));
            roms.add(Arguments.of("boot_hwio-S.gb"));
        }

        return roms.stream();
    }

    private MooneyeResult runMooneye(Path romPath, HardwareType hardwareType) throws Exception {
        GameBoyColor gbc = new GameBoyColor();
        gbc.getMemory().setHardwareType(hardwareType);

        Cartridge cartridge = new RomLoader().load(romPath);
        gbc.insertCartridge(cartridge);
        gbc.reset();

        Registers registers = gbc.getCpu().getRegisters();
        Instant start = Instant.now();
        long instructions = 0;

        while (instructions < MAX_INSTRUCTIONS
                && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {
            gbc.executeCycle();
            instructions++;
            if (isPassState(registers)) {
                return new MooneyeResult(true, registers.copy(), snapshotIo(gbc.getMemory()), snapshotFailContext(gbc.getMemory()));
            }
            if (isFailState(registers)) {
                return new MooneyeResult(false, registers.copy(), snapshotIo(gbc.getMemory()), snapshotFailContext(gbc.getMemory()));
            }
        }

        return new MooneyeResult(false, registers.copy(), snapshotIo(gbc.getMemory()), snapshotFailContext(gbc.getMemory()));
    }

    private static String snapshotIo(gbc.model.memory.Memory memory) {
        int[] regs = {
                0xFF00, 0xFF04, 0xFF05, 0xFF06, 0xFF07,
                0xFF10, 0xFF11, 0xFF12, 0xFF13, 0xFF14,
                0xFF24, 0xFF25, 0xFF26,
                0xFF40, 0xFF41, 0xFF42, 0xFF43, 0xFF44, 0xFF45, 0xFF46, 0xFF47, 0xFF48, 0xFF49,
                0xFF4A, 0xFF4B, 0xFF0F
        };
        StringBuilder sb = new StringBuilder();
        for (int addr : regs) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%04X=%02X", addr, memory.readByte(addr) & 0xFF));
        }
        return sb.toString();
    }

    private static String snapshotFailContext(gbc.model.memory.Memory memory) {
        int addrHi = memory.readByte(0xFF81) & 0xFF;
        int addrLo = memory.readByte(0xFF80) & 0xFF;
        int expected = memory.readByte(0xFF82) & 0xFF;
        int got = memory.readByte(0xFF83) & 0xFF;
        if (addrHi == 0 && addrLo == 0 && expected == 0 && got == 0) {
            return "";
        }
        return String.format("addr=%02X%02X expected=%02X got=%02X", addrHi, addrLo, expected, got);
    }

    private static boolean isPassState(Registers registers) {
        return (registers.getRegister("B") & 0xFF) == 3
                && (registers.getRegister("C") & 0xFF) == 5
                && (registers.getRegister("D") & 0xFF) == 8
                && (registers.getRegister("E") & 0xFF) == 13
                && (registers.getRegister("H") & 0xFF) == 21
                && (registers.getRegister("L") & 0xFF) == 34;
    }

    private static boolean isFailState(Registers registers) {
        return (registers.getRegister("B") & 0xFF) == 0x42
                && (registers.getRegister("C") & 0xFF) == 0x42
                && (registers.getRegister("D") & 0xFF) == 0x42
                && (registers.getRegister("E") & 0xFF) == 0x42
                && (registers.getRegister("H") & 0xFF) == 0x42
                && (registers.getRegister("L") & 0xFF) == 0x42;
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
        throw new IllegalStateException("Cannot locate mooneye-test-suite directory");
    }

    private record MooneyeResult(boolean passed, Registers registers, String ioSnapshot, String failContext) {}
}
