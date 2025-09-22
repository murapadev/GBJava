package gbc.mooneye;

import gbc.model.GameBoyColor;
import gbc.model.cpu.Registers;
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

        try {
            return Files.walk(acceptanceRoot)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".gb"))
                    .sorted(Comparator.comparing(path -> acceptanceRoot.relativize(path).toString()))
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
                return MooneyeResult.passed(cycles, instructions, registers);
            }
        }

        return MooneyeResult.failed(cycles, instructions, registers);
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

        return "Mooneye test did not reach pass state: " + displayName
                + " (" + romPath + ")\n"
                + "cycles=" + result.cycles() + ", instructions=" + result.instructions()
                + "\n" + registersDump;
    }

    private record MooneyeResult(boolean passed, long cycles, long instructions, Registers registers, int pc) {
        static MooneyeResult passed(long cycles, long instructions, Registers registers) {
            return new MooneyeResult(true, cycles, instructions, snapshot(registers), registers.getPC());
        }

        static MooneyeResult failed(long cycles, long instructions, Registers registers) {
            return new MooneyeResult(false, cycles, instructions, snapshot(registers), registers.getPC());
        }

        private static Registers snapshot(Registers source) {
            Registers copy = new Registers();
            copy.setAF(source.getAF());
            copy.setBC(source.getBC());
            copy.setDE(source.getDE());
            copy.setHL(source.getHL());
            copy.setPC(source.getPC());
            copy.setSP(source.getSP());
            return copy;
        }
    }
}
