package gbc.model.cpu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure in-code catalogue of CPU operations. The Game Boy ISA is organised here by families so we
 * can expand coverage incrementally without relying on external JSON descriptors.
 */
final class OperationDefinitions {

    private OperationDefinitions() {}

    static EnumMap<OperationType, Map<Integer, Operation>> buildUnprefixedTable() {
        EnumMap<OperationType, Map<Integer, Operation>> grouped = initGroups();
        registerMisc(grouped);
        registerLoadTransfers(grouped);
        registerArithmetic(grouped);
        registerLogic(grouped);
        registerIncDec(grouped);
        registerStack(grouped);
        return grouped;
    }

    static EnumMap<OperationType, Map<Integer, Operation>> buildCbPrefixedTable() {
        EnumMap<OperationType, Map<Integer, Operation>> grouped = initGroups();
        registerBitOperations(grouped);
        return grouped;
    }

    private static EnumMap<OperationType, Map<Integer, Operation>> initGroups() {
        EnumMap<OperationType, Map<Integer, Operation>> grouped = new EnumMap<>(OperationType.class);
        for (OperationType type : OperationType.values()) {
            grouped.put(type, new HashMap<>());
        }
        return grouped;
    }

    private static void registerMisc(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        register(grouped, OperationType.MISC, 0x00, "NOP", 1, cycles(4), false);

        // Return instructions keep emulator control flow ticking even before a full instruction set is defined.
        register(grouped, OperationType.CONTROL_FLOW, 0xC9, "RET", 1, cycles(16), false);
        register(grouped, OperationType.CONTROL_FLOW, 0xD9, "RETI", 1, cycles(16), false);
    }

    private static void registerLoadTransfers(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        OperationType type = OperationType.LOAD_TRANSFER;

        // Immediate loads into 8-bit registers and (HL)
        register(grouped, type, 0x06, "LD", 2, cycles(8), true, operands(reg("B"), imm("d8")));
        register(grouped, type, 0x0E, "LD", 2, cycles(8), true, operands(reg("C"), imm("d8")));
        register(grouped, type, 0x16, "LD", 2, cycles(8), true, operands(reg("D"), imm("d8")));
        register(grouped, type, 0x1E, "LD", 2, cycles(8), true, operands(reg("E"), imm("d8")));
        register(grouped, type, 0x26, "LD", 2, cycles(8), true, operands(reg("H"), imm("d8")));
        register(grouped, type, 0x2E, "LD", 2, cycles(8), true, operands(reg("L"), imm("d8")));
        register(grouped, type, 0x36, "LD", 2, cycles(12), true, operands(mem("HL"), imm("d8")));
        register(grouped, type, 0x3E, "LD", 2, cycles(8), true, operands(reg("A"), imm("d8")));

        // Load HL with immediate 16-bit value
        register(grouped, type, 0x21, "LD", 3, cycles(12), true, operands(reg("HL"), imm("d16")));

        // Register-transfer loads centred around accumulator interactions
        register(grouped, type, 0x47, "LD", 1, cycles(4), false, operands(reg("B"), reg("A")));
        register(grouped, type, 0x4F, "LD", 1, cycles(4), false, operands(reg("C"), reg("A")));
        register(grouped, type, 0x57, "LD", 1, cycles(4), false, operands(reg("D"), reg("A")));
        register(grouped, type, 0x5F, "LD", 1, cycles(4), false, operands(reg("E"), reg("A")));
        register(grouped, type, 0x67, "LD", 1, cycles(4), false, operands(reg("H"), reg("A")));
        register(grouped, type, 0x6F, "LD", 1, cycles(4), false, operands(reg("L"), reg("A")));
        register(grouped, type, 0x77, "LD", 1, cycles(8), false, operands(mem("HL"), reg("A")));

        register(grouped, type, 0x78, "LD", 1, cycles(4), false, operands(reg("A"), reg("B")));
        register(grouped, type, 0x79, "LD", 1, cycles(4), false, operands(reg("A"), reg("C")));
        register(grouped, type, 0x7A, "LD", 1, cycles(4), false, operands(reg("A"), reg("D")));
        register(grouped, type, 0x7B, "LD", 1, cycles(4), false, operands(reg("A"), reg("E")));
        register(grouped, type, 0x7C, "LD", 1, cycles(4), false, operands(reg("A"), reg("H")));
        register(grouped, type, 0x7D, "LD", 1, cycles(4), false, operands(reg("A"), reg("L")));
        register(grouped, type, 0x7E, "LD", 1, cycles(8), false, operands(reg("A"), mem("HL")));
        register(grouped, type, 0x7F, "LD", 1, cycles(4), false, operands(reg("A"), reg("A")));
    }

    private static void registerArithmetic(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        OperationType type = OperationType.ARITHMETIC;
        String[] targets = {"B", "C", "D", "E", "H", "L", "HL", "A"};
        boolean[] isRegister = {true, true, true, true, true, true, false, true};

        // ADD A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0x80 + i;
            register(grouped, type, opcode, "ADD", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(reg("A"), operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xC6, "ADD", 2, cycles(8), true, operands(reg("A"), imm("d8")));

        // ADC A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0x88 + i;
            register(grouped, type, opcode, "ADC", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(reg("A"), operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xCE, "ADC", 2, cycles(8), true, operands(reg("A"), imm("d8")));

        // SUB A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0x90 + i;
            register(grouped, type, opcode, "SUB", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xD6, "SUB", 2, cycles(8), true, operands(imm("d8")));

        // SBC A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0x98 + i;
            register(grouped, type, opcode, "SBC", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xDE, "SBC", 2, cycles(8), true, operands(imm("d8")));
    }

    private static void registerLogic(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        OperationType type = OperationType.LOGIC;
        String[] targets = {"B", "C", "D", "E", "H", "L", "HL", "A"};
        boolean[] isRegister = {true, true, true, true, true, true, false, true};

        // AND A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0xA0 + i;
            register(grouped, type, opcode, "AND", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xE6, "AND", 2, cycles(8), true, operands(imm("d8")));

        // XOR A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0xA8 + i;
            register(grouped, type, opcode, "XOR", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xEE, "XOR", 2, cycles(8), true, operands(imm("d8")));

        // OR A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0xB0 + i;
            register(grouped, type, opcode, "OR", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xF6, "OR", 2, cycles(8), true, operands(imm("d8")));

        // CP A, r/(HL)
        for (int i = 0; i < targets.length; i++) {
            int opcode = 0xB8 + i;
            register(grouped, type, opcode, "CP", 1, cycles(isRegister[i] ? 4 : 8), false,
                    operands(operand(targets[i], isRegister[i])));
        }
        register(grouped, type, 0xFE, "CP", 2, cycles(8), true, operands(imm("d8")));
    }

    private static void registerIncDec(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        OperationType type = OperationType.INC_DEC;
        String[] targets = {"B", "C", "D", "E", "H", "L", "HL", "A"};
        boolean[] isRegister = {true, true, true, true, true, true, false, true};
        int[] incOpcodes = {0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C};
        int[] decOpcodes = {0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D};

        for (int i = 0; i < targets.length; i++) {
            register(grouped, type, incOpcodes[i], "INC", 1, cycles(isRegister[i] ? 4 : 12), false,
                    operands(operand(targets[i], isRegister[i])));
            register(grouped, type, decOpcodes[i], "DEC", 1, cycles(isRegister[i] ? 4 : 12), false,
                    operands(operand(targets[i], isRegister[i])));
        }
    }

    private static void registerStack(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        OperationType type = OperationType.STACK_IO;
        register(grouped, type, 0xF5, "PUSH", 1, cycles(16), false, operands(reg("AF")));
        register(grouped, type, 0xC5, "PUSH", 1, cycles(16), false, operands(reg("BC")));
        register(grouped, type, 0xD5, "PUSH", 1, cycles(16), false, operands(reg("DE")));
        register(grouped, type, 0xE5, "PUSH", 1, cycles(16), false, operands(reg("HL")));

        register(grouped, type, 0xF1, "POP", 1, cycles(12), false, operands(reg("AF")));
        register(grouped, type, 0xC1, "POP", 1, cycles(12), false, operands(reg("BC")));
        register(grouped, type, 0xD1, "POP", 1, cycles(12), false, operands(reg("DE")));
        register(grouped, type, 0xE1, "POP", 1, cycles(12), false, operands(reg("HL")));
    }

    private static void registerBitOperations(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        OperationType type = OperationType.BIT_MANIPULATION;
        String[] targets = {"B", "C", "D", "E", "H", "L", "HL", "A"};
        boolean[] isRegister = {true, true, true, true, true, true, false, true};

        for (int bit = 0; bit < 8; bit++) {
            for (int index = 0; index < targets.length; index++) {
                int base = bit * 8 + index;
                int bitOpcode = 0x40 + base;
                int resOpcode = 0x80 + base;
                int setOpcode = 0xC0 + base;

                register(grouped, type, bitOpcode, "BIT", 2, cycles(isRegister[index] ? 8 : 12), false,
                        operands(bitOperand(bit), operand(targets[index], isRegister[index])));
                register(grouped, type, resOpcode, "RES", 2, cycles(isRegister[index] ? 8 : 16), false,
                        operands(bitOperand(bit), operand(targets[index], isRegister[index])));
                register(grouped, type, setOpcode, "SET", 2, cycles(isRegister[index] ? 8 : 16), false,
                        operands(bitOperand(bit), operand(targets[index], isRegister[index])));
            }
        }
    }

    private static void register(EnumMap<OperationType, Map<Integer, Operation>> grouped,
                                 OperationType type,
                                 int opcode,
                                 String mnemonic,
                                 int bytes,
                                 List<Integer> cycles,
                                 boolean immediate) {
        register(grouped, type, opcode, mnemonic, bytes, cycles, immediate, List.of());
    }

    private static void register(EnumMap<OperationType, Map<Integer, Operation>> grouped,
                                 OperationType type,
                                 int opcode,
                                 String mnemonic,
                                 int bytes,
                                 List<Integer> cycles,
                                 boolean immediate,
                                 List<Map<String, Object>> operands) {
        Operation operation = new Operation(null, mnemonic, bytes, cycles, immediate, Map.of(), new ArrayList<>(operands));
        operation.setType(type);
        grouped.computeIfAbsent(type, ignored -> new HashMap<>())
                .put(opcode & 0xFF, operation);
    }

    private static List<Integer> cycles(int... values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    @SafeVarargs
    private static List<Map<String, Object>> operands(Map<String, Object>... operandList) {
        List<Map<String, Object>> result = new ArrayList<>(operandList.length);
        for (Map<String, Object> operand : operandList) {
            result.add(operand);
        }
        return result;
    }

    private static Map<String, Object> reg(String name) {
        return operand(name, true);
    }

    private static Map<String, Object> mem(String alias) {
        return operand(alias, false);
    }

    private static Map<String, Object> imm(String alias) {
        return operand(alias, true);
    }

    private static Map<String, Object> operand(String name, boolean immediate) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("immediate", immediate);
        return map;
    }

    private static Map<String, Object> bitOperand(int bit) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", Integer.toString(bit));
        map.put("immediate", true);
        return map;
    }
}
