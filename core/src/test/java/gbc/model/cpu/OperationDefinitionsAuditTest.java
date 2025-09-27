package gbc.model.cpu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OperationDefinitionsAuditTest {

    private static final int[] INVALID_UNPREFIXED_OPCODES = { 0xD3, 0xDB, 0xDD, 0xE3, 0xE4, 0xEB, 0xEC, 0xED, 0xF4,
            0xFC,
            0xFD };
    private static final Map<Integer, Expected> UNPREFIXED_EXPECTATIONS = buildUnprefixedExpectations();
    private static final Map<Integer, Expected> CB_EXPECTATIONS = buildCbExpectations();

    @Test
    void unprefixedOpcodesMatchReference() {
        OperationsLoader loader = new OperationsLoader();

        assertEquals(255, countDefined(loader), "Expected 255 defined unprefixed opcodes (all except 0xCB)");

        for (int opcode = 0; opcode < 0x100; opcode++) {
            final int currentOpcode = opcode;
            Operation operation = loader.getOperation(currentOpcode);
            if (currentOpcode == 0xCB) {
                assertNull(operation, "0xCB should be handled as prefix and not have a direct operation");
                continue;
            }

            Expected expected = UNPREFIXED_EXPECTATIONS.get(currentOpcode);
            assertNotNull(expected, () -> String.format("Missing expectation for opcode 0x%02X", currentOpcode));
            assertNotNull(operation, () -> String.format("Operation missing for opcode 0x%02X", currentOpcode));
            assertEquals(expected.mnemonic(), operation.getMnemonic(),
                    () -> String.format("Mnemonic mismatch for opcode 0x%02X", currentOpcode));
            assertEquals(expected.bytes(), operation.getBytes(),
                    () -> String.format("Length mismatch for opcode 0x%02X", currentOpcode));
            assertEquals(expected.cycles(), operation.getCycles(),
                    () -> String.format("Cycle mismatch for opcode 0x%02X", currentOpcode));
        }
    }

    @Test
    void cbPrefixedOpcodesMatchReference() {
        OperationsLoader loader = new OperationsLoader();

        for (int opcode = 0; opcode < 0x100; opcode++) {
            final int currentOpcode = opcode;
            Operation operation = loader.getCbOperation(currentOpcode);
            Expected expected = CB_EXPECTATIONS.get(currentOpcode);
            assertNotNull(expected, () -> String.format("Missing CB expectation for opcode 0xCB%02X", currentOpcode));
            assertNotNull(operation, () -> String.format("CB operation missing for opcode 0xCB%02X", currentOpcode));
            assertEquals(expected.mnemonic(), operation.getMnemonic(),
                    () -> String.format("CB mnemonic mismatch for opcode 0xCB%02X", currentOpcode));
            assertEquals(expected.bytes(), operation.getBytes(),
                    () -> String.format("CB length mismatch for opcode 0xCB%02X", currentOpcode));
            assertEquals(expected.cycles(), operation.getCycles(),
                    () -> String.format("CB cycle mismatch for opcode 0xCB%02X", currentOpcode));
        }
    }

    @Test
    void unprefixedOperationsExposeExecutors() {
        OperationsLoader loader = new OperationsLoader();

        for (int opcode = 0; opcode < 0x100; opcode++) {
            if (opcode == 0xCB)
                continue;
            final int currentOpcode = opcode;
            Operation operation = loader.getOperation(currentOpcode);
            assertNotNull(operation, () -> String.format("Operation missing for opcode 0x%02X", currentOpcode));
            assertNotNull(operation.getExecutor(),
                    () -> String.format("Executor missing for opcode 0x%02X", currentOpcode));
        }
    }

    @Test
    void cbPrefixedOperationsExposeExecutors() {
        OperationsLoader loader = new OperationsLoader();

        for (int opcode = 0; opcode < 0x100; opcode++) {
            final int currentOpcode = opcode;
            Operation operation = loader.getCbOperation(currentOpcode);
            assertNotNull(operation,
                    () -> String.format("CB operation missing for opcode 0xCB%02X", currentOpcode));
            assertNotNull(operation.getExecutor(),
                    () -> String.format("Executor missing for opcode 0xCB%02X", currentOpcode));
        }
    }

    private static int countDefined(OperationsLoader loader) {
        int count = 0;
        for (int opcode = 0; opcode < 0x100; opcode++) {
            if (loader.getOperation(opcode) != null) {
                count++;
            }
        }
        return count;
    }

    private static Map<Integer, Expected> buildUnprefixedExpectations() {
        Map<Integer, Expected> map = new HashMap<>();

        // Row 0x0x
        put(map, 0x00, "NOP", 1, 4);
        put(map, 0x01, "LD", 3, 12);
        put(map, 0x02, "LD", 1, 8);
        put(map, 0x03, "INC", 1, 8);
        put(map, 0x04, "INC", 1, 4);
        put(map, 0x05, "DEC", 1, 4);
        put(map, 0x06, "LD", 2, 8);
        put(map, 0x07, "RLCA", 1, 4);
        put(map, 0x08, "LD", 3, 20);
        put(map, 0x09, "ADD", 1, 8);
        put(map, 0x0A, "LD", 1, 8);
        put(map, 0x0B, "DEC", 1, 8);
        put(map, 0x0C, "INC", 1, 4);
        put(map, 0x0D, "DEC", 1, 4);
        put(map, 0x0E, "LD", 2, 8);
        put(map, 0x0F, "RRCA", 1, 4);

        // Row 0x1x
        put(map, 0x10, "STOP", 2, 4);
        put(map, 0x11, "LD", 3, 12);
        put(map, 0x12, "LD", 1, 8);
        put(map, 0x13, "INC", 1, 8);
        put(map, 0x14, "INC", 1, 4);
        put(map, 0x15, "DEC", 1, 4);
        put(map, 0x16, "LD", 2, 8);
        put(map, 0x17, "RLA", 1, 4);
        put(map, 0x18, "JR", 2, 12);
        put(map, 0x19, "ADD", 1, 8);
        put(map, 0x1A, "LD", 1, 8);
        put(map, 0x1B, "DEC", 1, 8);
        put(map, 0x1C, "INC", 1, 4);
        put(map, 0x1D, "DEC", 1, 4);
        put(map, 0x1E, "LD", 2, 8);
        put(map, 0x1F, "RRA", 1, 4);

        // Row 0x2x
        put(map, 0x20, "JR", 2, 8, 12);
        put(map, 0x21, "LD", 3, 12);
        put(map, 0x22, "LD", 1, 8);
        put(map, 0x23, "INC", 1, 8);
        put(map, 0x24, "INC", 1, 4);
        put(map, 0x25, "DEC", 1, 4);
        put(map, 0x26, "LD", 2, 8);
        put(map, 0x27, "DAA", 1, 4);
        put(map, 0x28, "JR", 2, 8, 12);
        put(map, 0x29, "ADD", 1, 8);
        put(map, 0x2A, "LD", 1, 8);
        put(map, 0x2B, "DEC", 1, 8);
        put(map, 0x2C, "INC", 1, 4);
        put(map, 0x2D, "DEC", 1, 4);
        put(map, 0x2E, "LD", 2, 8);
        put(map, 0x2F, "CPL", 1, 4);

        // Row 0x3x
        put(map, 0x30, "JR", 2, 8, 12);
        put(map, 0x31, "LD", 3, 12);
        put(map, 0x32, "LD", 1, 8);
        put(map, 0x33, "INC", 1, 8);
        put(map, 0x34, "INC", 1, 12);
        put(map, 0x35, "DEC", 1, 12);
        put(map, 0x36, "LD", 2, 12);
        put(map, 0x37, "SCF", 1, 4);
        put(map, 0x38, "JR", 2, 8, 12);
        put(map, 0x39, "ADD", 1, 8);
        put(map, 0x3A, "LD", 1, 8);
        put(map, 0x3B, "DEC", 1, 8);
        put(map, 0x3C, "INC", 1, 4);
        put(map, 0x3D, "DEC", 1, 4);
        put(map, 0x3E, "LD", 2, 8);
        put(map, 0x3F, "CCF", 1, 4);

        // LD r,r' block (0x40-0x7F) except 0x76 (HALT)
        String[] regs = { "B", "C", "D", "E", "H", "L", "(HL)", "A" };
        for (int dest = 0; dest < regs.length; dest++) {
            for (int src = 0; src < regs.length; src++) {
                int opcode = 0x40 | (dest << 3) | src;
                if (opcode == 0x76) {
                    put(map, 0x76, "HALT", 1, 4);
                    continue;
                }
                int cycles = (dest == 6 || src == 6) ? 8 : 4;
                put(map, opcode, "LD", 1, cycles);
            }
        }

        // ADD/ADC/SUB/SBC/AND/XOR/OR/CP register variants
        String[] arithmeticMnemonics = { "ADD", "ADC", "SUB", "SBC", "AND", "XOR", "OR", "CP" };
        int[] arithmeticBases = { 0x80, 0x88, 0x90, 0x98, 0xA0, 0xA8, 0xB0, 0xB8 };
        for (int group = 0; group < arithmeticMnemonics.length; group++) {
            for (int idx = 0; idx < regs.length; idx++) {
                int opcode = arithmeticBases[group] + idx;
                int cycles = (idx == 6) ? 8 : 4;
                put(map, opcode, arithmeticMnemonics[group], 1, cycles);
            }
        }

        // Immediate arithmetic/logical ops
        put(map, 0xC6, "ADD", 2, 8);
        put(map, 0xCE, "ADC", 2, 8);
        put(map, 0xD6, "SUB", 2, 8);
        put(map, 0xDE, "SBC", 2, 8);
        put(map, 0xE6, "AND", 2, 8);
        put(map, 0xEE, "XOR", 2, 8);
        put(map, 0xF6, "OR", 2, 8);
        put(map, 0xFE, "CP", 2, 8);

        // ADD SP, r8
        put(map, 0xE8, "ADD", 2, 16);

        // Stack operations
        put(map, 0xC5, "PUSH", 1, 16);
        put(map, 0xD5, "PUSH", 1, 16);
        put(map, 0xE5, "PUSH", 1, 16);
        put(map, 0xF5, "PUSH", 1, 16);
        put(map, 0xC1, "POP", 1, 12);
        put(map, 0xD1, "POP", 1, 12);
        put(map, 0xE1, "POP", 1, 12);
        put(map, 0xF1, "POP", 1, 12);

        // LD special cases
        put(map, 0xF8, "LD", 2, 12);
        put(map, 0xF9, "LD", 1, 8);
        put(map, 0xFA, "LD", 3, 16);
        put(map, 0xEA, "LD", 3, 16);
        put(map, 0xE0, "LDH", 2, 12);
        put(map, 0xF0, "LDH", 2, 12);
        put(map, 0xE2, "LDH", 1, 8);
        put(map, 0xF2, "LDH", 1, 8);

        // Control flow
        put(map, 0xC3, "JP", 3, 16);
        put(map, 0xC2, "JP", 3, 12, 16);
        put(map, 0xCA, "JP", 3, 12, 16);
        put(map, 0xD2, "JP", 3, 12, 16);
        put(map, 0xDA, "JP", 3, 12, 16);
        put(map, 0xE9, "JP", 1, 4);

        put(map, 0xCD, "CALL", 3, 24);
        put(map, 0xC4, "CALL", 3, 12, 24);
        put(map, 0xCC, "CALL", 3, 12, 24);
        put(map, 0xD4, "CALL", 3, 12, 24);
        put(map, 0xDC, "CALL", 3, 12, 24);

        put(map, 0xC9, "RET", 1, 16);
        put(map, 0xC0, "RET", 1, 8, 20);
        put(map, 0xC8, "RET", 1, 8, 20);
        put(map, 0xD0, "RET", 1, 8, 20);
        put(map, 0xD8, "RET", 1, 8, 20);
        put(map, 0xD9, "RETI", 1, 16);

        int[] rstBases = { 0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF };
        for (int opcode : rstBases) {
            put(map, opcode, "RST", 1, 16);
        }

        // Misc operations
        put(map, 0xF3, "DI", 1, 4);
        put(map, 0xFB, "EI", 1, 4);

        // Invalid opcodes
        for (int invalid : INVALID_UNPREFIXED_OPCODES) {
            put(map, invalid, "INVALID", 1, 4);
        }

        return Map.copyOf(map);
    }

    private static Map<Integer, Expected> buildCbExpectations() {
        Map<Integer, Expected> map = new HashMap<>();
        String[] regs = { "B", "C", "D", "E", "H", "L", "(HL)", "A" };

        int[] rotateOffsets = { 0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38 };
        String[] rotateMnemonics = { "RLC", "RRC", "RL", "RR", "SLA", "SRA", "SWAP", "SRL" };
        for (int group = 0; group < rotateMnemonics.length; group++) {
            for (int idx = 0; idx < regs.length; idx++) {
                int opcode = rotateOffsets[group] + idx;
                int cycles = (idx == 6) ? 16 : 8;
                put(map, opcode, rotateMnemonics[group], 2, cycles);
            }
        }

        for (int bit = 0; bit < 8; bit++) {
            for (int idx = 0; idx < regs.length; idx++) {
                int base = bit * 8 + idx;
                int bitOpcode = 0x40 + base;
                int resOpcode = 0x80 + base;
                int setOpcode = 0xC0 + base;

                int bitCycles = (idx == 6) ? 12 : 8;
                int resCycles = (idx == 6) ? 16 : 8;
                int setCycles = (idx == 6) ? 16 : 8;

                put(map, bitOpcode, "BIT", 2, bitCycles);
                put(map, resOpcode, "RES", 2, resCycles);
                put(map, setOpcode, "SET", 2, setCycles);
            }
        }

        return Map.copyOf(map);
    }

    private static void put(Map<Integer, Expected> map, int opcode, String mnemonic, int bytes, int... cycles) {
        List<Integer> cycleList = Arrays.stream(cycles).boxed().toList();
        Expected previous = map.put(opcode & 0xFF, new Expected(mnemonic, bytes, cycleList));
        if (previous != null) {
            throw new IllegalStateException(String.format("Duplicate expectation for opcode 0x%02X", opcode & 0xFF));
        }
    }

    private record Expected(String mnemonic, int bytes, List<Integer> cycles) {
    }
}
