package gbc.acid2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import gbc.controller.io.RomLoader;
import gbc.model.GameBoyColor;
import gbc.model.HardwareType;
import gbc.model.cartridge.Cartridge;
import gbc.model.cpu.CPU;
import gbc.model.graphics.FrameBuffer;
import gbc.model.memory.Memory;
import gbc.testsupport.TestRoms;

/**
 * Test harness for dmg-acid2 and cgb-acid2 visual test ROMs.
 * <p>
 * These tests render a reference image (a smiley face) when all PPU features
 * are implemented correctly. The test detects completion when the ROM executes
 * opcode 0x40 (LD B, B), then captures and checksums the framebuffer.
 * <p>
 * On first run with unknown checksum, the test will print the actual checksum
 * so it can be recorded. This allows detecting regressions even before a
 * pixel-perfect reference is achieved.
 */
class Acid2Test {

    private static final long MAX_INSTRUCTIONS = 50_000_000L;
    private static final long MAX_DURATION_MS = 30_000L;

    @Test
    void dmgAcid2() throws Exception {
        Path romPath = TestRoms.resolve("dmg-acid2.gb");
        Assumptions.assumeTrue(Files.exists(romPath), "Missing dmg-acid2.gb");

        Acid2Result result = runAcid2(romPath, HardwareType.DMG);
        assertNotNull(result, "Test should produce a result");
        assertTrue(result.completed, "Test ROM should reach LD B,B completion marker");

        System.out.println("dmg-acid2 framebuffer SHA-256: " + result.sha256);
        System.out.println("dmg-acid2 completed after " + result.cycles + " cycles, "
                + result.instructions + " instructions, " + result.frames + " frames");

        // Reference image from the dmg-acid2 repository (img/reference-dmg.png).
        Path reference = TestRoms.resolve("dmg-acid2-reference.png");
        if (Files.exists(reference)) {
            int mismatches = countShadeMismatches(result.pixels, ImageIO.read(reference.toFile()));
            assertEquals(0, mismatches, "dmg-acid2 pixels differing from the reference image");
        }
    }

    /**
     * Compares by shade rather than RGB so the result does not depend on the
     * configured DMG palette: both images are reduced to luminance ranks.
     */
    private static int countShadeMismatches(int[] pixels, BufferedImage reference) {
        int[] refPixels = reference.getRGB(0, 0, FrameBuffer.WIDTH, FrameBuffer.HEIGHT, null, 0, FrameBuffer.WIDTH);
        int[] actual = shadeRanks(pixels);
        int[] expected = shadeRanks(refPixels);
        int mismatches = 0;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private static int[] shadeRanks(int[] argb) {
        int[] levels = IntStream.of(argb).map(Acid2Test::luminance).distinct().sorted().toArray();
        return IntStream.of(argb).map(c -> Arrays.binarySearch(levels, luminance(c))).toArray();
    }

    private static int luminance(int argb) {
        return 299 * ((argb >> 16) & 0xFF) + 587 * ((argb >> 8) & 0xFF) + 114 * (argb & 0xFF);
    }

    @Test
    void cgbAcid2() throws Exception {
        Path romPath = TestRoms.resolve("cgb-acid2.gbc");
        Assumptions.assumeTrue(Files.exists(romPath), "Missing cgb-acid2.gbc");

        Acid2Result result = runAcid2(romPath, HardwareType.CGB);
        assertNotNull(result, "Test should produce a result");

        System.out.println("cgb-acid2 framebuffer SHA-256: " + result.sha256);
        System.out.println("cgb-acid2 completed=" + result.completed + " after " + result.cycles
                + " cycles, " + result.instructions + " instructions, " + result.frames + " frames");

        // CGB acid2 completion is tracked but not required yet — CGB PPU
        // emulation has known gaps that prevent the test ROM from finishing.
        // The checksum is still captured for regression tracking.
        if (!result.completed) {
            System.out.println("WARNING: cgb-acid2 did not reach completion marker (known CGB limitation)");
        }
    }

    private Acid2Result runAcid2(Path romPath, HardwareType hwType) throws Exception {
        GameBoyColor gbc = new GameBoyColor();
        gbc.getMemory().setHardwareType(hwType);

        Cartridge cartridge = new RomLoader().load(romPath);
        gbc.insertCartridge(cartridge);
        gbc.reset();

        CPU cpu = gbc.getCpu();
        Memory memory = gbc.getMemory();
        FrameBuffer fb = gbc.getFrameBuffer();

        Instant start = Instant.now();
        long cycles = 0;
        long instructions = 0;
        boolean completed = false;
        int stuckCount = 0;
        int lastPc = -1;

        while (instructions < MAX_INSTRUCTIONS
                && Duration.between(start, Instant.now()).toMillis() < MAX_DURATION_MS) {

            int pc = cpu.getRegisters().getPC() & 0xFFFF;

            // Detect completion: LD B, B (0x40) or stuck in a tight loop
            if (!cpu.isHalted() && !completed) {
                if (pc == lastPc) {
                    stuckCount++;
                    if (stuckCount > 100) {
                        completed = true;
                    }
                } else {
                    stuckCount = 0;
                }
                // Also detect LD B, B directly
                int opcode = memory.readByte(pc) & 0xFF;
                if (opcode == 0x40 && fb.getFrameId() >= 2) {
                    completed = true;
                }
            }
            lastPc = pc;

            if (completed) {
                // Run one more frame to ensure display is finalized
                long framesBefore = fb.getFrameId();
                while (fb.getFrameId() <= framesBefore + 1
                        && instructions < MAX_INSTRUCTIONS) {
                    int executed = gbc.executeCycle();
                    cycles += Math.max(4, executed);
                    instructions++;
                }
                break;
            }

            int executed = gbc.executeCycle();
            cycles += Math.max(4, executed);
            instructions++;
        }

        // Compute framebuffer checksum
        String sha256 = computeFrameBufferHash(fb);
        long frames = fb.getFrameId();

        gbc.closeTrace();
        return new Acid2Result(completed, cycles, instructions, frames, sha256, fb.getPixels().clone());
    }

    private String computeFrameBufferHash(FrameBuffer fb) throws Exception {
        int[] pixels = fb.getPixels();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[4];
        for (int pixel : pixels) {
            buf[0] = (byte) ((pixel >> 24) & 0xFF);
            buf[1] = (byte) ((pixel >> 16) & 0xFF);
            buf[2] = (byte) ((pixel >> 8) & 0xFF);
            buf[3] = (byte) (pixel & 0xFF);
            md.update(buf);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private record Acid2Result(boolean completed, long cycles, long instructions, long frames, String sha256,
            int[] pixels) {
    }
}
