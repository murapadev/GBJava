package gbc.model.cpu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure in-code catalogue of CPU operations.
 * Groups opcodes by OperationType and exposes prebuilt tables for
 * unprefixed and CB-prefixed spaces.
 */
final class OperationDefinitions {
        private OperationDefinitions() {
        }

        static EnumMap<OperationType, Map<Integer, Operation>> buildUnprefixedTable() {
                EnumMap<OperationType, Map<Integer, Operation>> grouped = initGroups();
                registerMisc(grouped);
                registerLoadTransfers(grouped);
                registerArithmetic(grouped);
                registerLogic(grouped);
                registerIncDec(grouped);
                registerStack(grouped); // kept for symmetry; stack ops live in LD section
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
                // NOP
                register(grouped, OperationType.MISC, 0x00, "NOP", 1, cycles(4), false);

                // Rotates (accumulator)
                register(grouped, OperationType.BIT_MANIPULATION, 0x07, "RLCA", 1, cycles(4), false);
                register(grouped, OperationType.BIT_MANIPULATION, 0x0F, "RRCA", 1, cycles(4), false);
                register(grouped, OperationType.BIT_MANIPULATION, 0x17, "RLA", 1, cycles(4), false);
                register(grouped, OperationType.BIT_MANIPULATION, 0x1F, "RRA", 1, cycles(4), false);

                // Interrupt control
                register(grouped, OperationType.MISC, 0xF3, "DI", 1, cycles(4), false);
                register(grouped, OperationType.MISC, 0xFB, "EI", 1, cycles(4), false);

                // Flag ops
                register(grouped, OperationType.MISC, 0x37, "SCF", 1, cycles(4), false);
                register(grouped, OperationType.MISC, 0x3F, "CCF", 1, cycles(4), false);
                register(grouped, OperationType.MISC, 0x2F, "CPL", 1, cycles(4), false);
                register(grouped, OperationType.MISC, 0x27, "DAA", 1, cycles(4), false);

                // Halt / Stop
                register(grouped, OperationType.MISC, 0x76, "HALT", 1, cycles(4), false);
                // STOP is 2 bytes; second should be 0x00. We mark "immediate" to signal it has
                // a second byte.
                register(grouped, OperationType.MISC, 0x10, "STOP", 2, cycles(4), true, operands());

                // JR family
                register(grouped, OperationType.CONTROL_FLOW, 0x18, "JR", 2, cycles(12), true, operands(imm("r8")));
                register(grouped, OperationType.CONTROL_FLOW, 0x20, "JR", 2, cycles(8, 12), true,
                                operands(imm("NZ"), imm("r8")));
                register(grouped, OperationType.CONTROL_FLOW, 0x28, "JR", 2, cycles(8, 12), true,
                                operands(imm("Z"), imm("r8")));
                register(grouped, OperationType.CONTROL_FLOW, 0x30, "JR", 2, cycles(8, 12), true,
                                operands(imm("NC"), imm("r8")));
                register(grouped, OperationType.CONTROL_FLOW, 0x38, "JR", 2, cycles(8, 12), true,
                                operands(imm("C"), imm("r8")));

                // JP family
                register(grouped, OperationType.CONTROL_FLOW, 0xC3, "JP", 3, cycles(16), true, operands(imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xC2, "JP", 3, cycles(12, 16), true,
                                operands(imm("NZ"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xCA, "JP", 3, cycles(12, 16), true,
                                operands(imm("Z"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xD2, "JP", 3, cycles(12, 16), true,
                                operands(imm("NC"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xDA, "JP", 3, cycles(12, 16), true,
                                operands(imm("C"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xE9, "JP", 1, cycles(4), false, operands(reg("HL")));

                // CALL family
                register(grouped, OperationType.CONTROL_FLOW, 0xCD, "CALL", 3, cycles(24), true, operands(imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xC4, "CALL", 3, cycles(12, 24), true,
                                operands(imm("NZ"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xCC, "CALL", 3, cycles(12, 24), true,
                                operands(imm("Z"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xD4, "CALL", 3, cycles(12, 24), true,
                                operands(imm("NC"), imm("a16")));
                register(grouped, OperationType.CONTROL_FLOW, 0xDC, "CALL", 3, cycles(12, 24), true,
                                operands(imm("C"), imm("a16")));

                // RET / RETI
                register(grouped, OperationType.CONTROL_FLOW, 0xC9, "RET", 1, cycles(16), true);
                register(grouped, OperationType.CONTROL_FLOW, 0xC0, "RET", 1, cycles(8, 20), true, operands(imm("NZ")));
                register(grouped, OperationType.CONTROL_FLOW, 0xC8, "RET", 1, cycles(8, 20), true, operands(imm("Z")));
                register(grouped, OperationType.CONTROL_FLOW, 0xD0, "RET", 1, cycles(8, 20), true, operands(imm("NC")));
                register(grouped, OperationType.CONTROL_FLOW, 0xD8, "RET", 1, cycles(8, 20), true, operands(imm("C")));
                register(grouped, OperationType.CONTROL_FLOW, 0xD9, "RETI", 1, cycles(16), true);

                // RST
                register(grouped, OperationType.CONTROL_FLOW, 0xC7, "RST", 1, cycles(16), true, operands(imm("00H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xCF, "RST", 1, cycles(16), true, operands(imm("08H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xD7, "RST", 1, cycles(16), true, operands(imm("10H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xDF, "RST", 1, cycles(16), true, operands(imm("18H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xE7, "RST", 1, cycles(16), true, operands(imm("20H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xEF, "RST", 1, cycles(16), true, operands(imm("28H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xF7, "RST", 1, cycles(16), true, operands(imm("30H")));
                register(grouped, OperationType.CONTROL_FLOW, 0xFF, "RST", 1, cycles(16), true, operands(imm("38H")));

                // Invalid/unused (DMG)
                int[] invalids = { 0xD3, 0xDB, 0xDD, 0xE3, 0xE4, 0xEB, 0xEC, 0xED, 0xF4, 0xFC, 0xFD };
                for (int opc : invalids) {
                        register(grouped, OperationType.MISC, opc, "INVALID", 1, cycles(4), false);
                }
        }

        private static void registerLoadTransfers(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
                OperationType type = OperationType.LOAD_TRANSFER;

                // LD r, d8 / (HL), d8
                register(grouped, type, 0x06, "LD", 2, cycles(8), true, operands(reg("B"), imm("d8")));
                register(grouped, type, 0x0E, "LD", 2, cycles(8), true, operands(reg("C"), imm("d8")));
                register(grouped, type, 0x16, "LD", 2, cycles(8), true, operands(reg("D"), imm("d8")));
                register(grouped, type, 0x1E, "LD", 2, cycles(8), true, operands(reg("E"), imm("d8")));
                register(grouped, type, 0x26, "LD", 2, cycles(8), true, operands(reg("H"), imm("d8")));
                register(grouped, type, 0x2E, "LD", 2, cycles(8), true, operands(reg("L"), imm("d8")));
                register(grouped, type, 0x36, "LD", 2, cycles(12), true, operands(mem("HL"), imm("d8")));
                register(grouped, type, 0x3E, "LD", 2, cycles(8), true, operands(reg("A"), imm("d8")));

                // LD rr, d16
                register(grouped, type, 0x01, "LD", 3, cycles(12), true, operands(reg("BC"), imm("d16")));
                register(grouped, type, 0x11, "LD", 3, cycles(12), true, operands(reg("DE"), imm("d16")));
                register(grouped, type, 0x21, "LD", 3, cycles(12), true, operands(reg("HL"), imm("d16")));
                register(grouped, type, 0x31, "LD", 3, cycles(12), true, operands(reg("SP"), imm("d16")));

                // LD r,r / r,(HL) / (HL),r / A,<r> and <r>,A
                // Accumulator-centric
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

                // Full 8-bit LD matrix (minus opcodes above)
                register(grouped, type, 0x40, "LD", 1, cycles(4), false, operands(reg("B"), reg("B")));
                register(grouped, type, 0x41, "LD", 1, cycles(4), false, operands(reg("B"), reg("C")));
                register(grouped, type, 0x42, "LD", 1, cycles(4), false, operands(reg("B"), reg("D")));
                register(grouped, type, 0x43, "LD", 1, cycles(4), false, operands(reg("B"), reg("E")));
                register(grouped, type, 0x44, "LD", 1, cycles(4), false, operands(reg("B"), reg("H")));
                register(grouped, type, 0x45, "LD", 1, cycles(4), false, operands(reg("B"), reg("L")));
                register(grouped, type, 0x46, "LD", 1, cycles(8), false, operands(reg("B"), mem("HL")));
                register(grouped, type, 0x48, "LD", 1, cycles(4), false, operands(reg("C"), reg("B")));
                register(grouped, type, 0x49, "LD", 1, cycles(4), false, operands(reg("C"), reg("C")));
                register(grouped, type, 0x4A, "LD", 1, cycles(4), false, operands(reg("C"), reg("D")));
                register(grouped, type, 0x4B, "LD", 1, cycles(4), false, operands(reg("C"), reg("E")));
                register(grouped, type, 0x4C, "LD", 1, cycles(4), false, operands(reg("C"), reg("H")));
                register(grouped, type, 0x4D, "LD", 1, cycles(4), false, operands(reg("C"), reg("L")));
                register(grouped, type, 0x4E, "LD", 1, cycles(8), false, operands(reg("C"), mem("HL")));
                register(grouped, type, 0x50, "LD", 1, cycles(4), false, operands(reg("D"), reg("B")));
                register(grouped, type, 0x51, "LD", 1, cycles(4), false, operands(reg("D"), reg("C")));
                register(grouped, type, 0x52, "LD", 1, cycles(4), false, operands(reg("D"), reg("D")));
                register(grouped, type, 0x53, "LD", 1, cycles(4), false, operands(reg("D"), reg("E")));
                register(grouped, type, 0x54, "LD", 1, cycles(4), false, operands(reg("D"), reg("H")));
                register(grouped, type, 0x55, "LD", 1, cycles(4), false, operands(reg("D"), reg("L")));
                register(grouped, type, 0x56, "LD", 1, cycles(8), false, operands(reg("D"), mem("HL")));
                register(grouped, type, 0x58, "LD", 1, cycles(4), false, operands(reg("E"), reg("B")));
                register(grouped, type, 0x59, "LD", 1, cycles(4), false, operands(reg("E"), reg("C")));
                register(grouped, type, 0x5A, "LD", 1, cycles(4), false, operands(reg("E"), reg("D")));
                register(grouped, type, 0x5B, "LD", 1, cycles(4), false, operands(reg("E"), reg("E")));
                register(grouped, type, 0x5C, "LD", 1, cycles(4), false, operands(reg("E"), reg("H")));
                register(grouped, type, 0x5D, "LD", 1, cycles(4), false, operands(reg("E"), reg("L")));
                register(grouped, type, 0x5E, "LD", 1, cycles(8), false, operands(reg("E"), mem("HL")));
                register(grouped, type, 0x60, "LD", 1, cycles(4), false, operands(reg("H"), reg("B")));
                register(grouped, type, 0x61, "LD", 1, cycles(4), false, operands(reg("H"), reg("C")));
                register(grouped, type, 0x62, "LD", 1, cycles(4), false, operands(reg("H"), reg("D")));
                register(grouped, type, 0x63, "LD", 1, cycles(4), false, operands(reg("H"), reg("E")));
                register(grouped, type, 0x64, "LD", 1, cycles(4), false, operands(reg("H"), reg("H")));
                register(grouped, type, 0x65, "LD", 1, cycles(4), false, operands(reg("H"), reg("L")));
                register(grouped, type, 0x66, "LD", 1, cycles(8), false, operands(reg("H"), mem("HL")));
                register(grouped, type, 0x68, "LD", 1, cycles(4), false, operands(reg("L"), reg("B")));
                register(grouped, type, 0x69, "LD", 1, cycles(4), false, operands(reg("L"), reg("C")));
                register(grouped, type, 0x6A, "LD", 1, cycles(4), false, operands(reg("L"), reg("D")));
                register(grouped, type, 0x6B, "LD", 1, cycles(4), false, operands(reg("L"), reg("E")));
                register(grouped, type, 0x6C, "LD", 1, cycles(4), false, operands(reg("L"), reg("H")));
                register(grouped, type, 0x6D, "LD", 1, cycles(4), false, operands(reg("L"), reg("L")));
                register(grouped, type, 0x6E, "LD", 1, cycles(8), false, operands(reg("L"), mem("HL")));
                register(grouped, type, 0x70, "LD", 1, cycles(8), false, operands(mem("HL"), reg("B")));
                register(grouped, type, 0x71, "LD", 1, cycles(8), false, operands(mem("HL"), reg("C")));
                register(grouped, type, 0x72, "LD", 1, cycles(8), false, operands(mem("HL"), reg("D")));
                register(grouped, type, 0x73, "LD", 1, cycles(8), false, operands(mem("HL"), reg("E")));
                register(grouped, type, 0x74, "LD", 1, cycles(8), false, operands(mem("HL"), reg("H")));
                register(grouped, type, 0x75, "LD", 1, cycles(8), false, operands(mem("HL"), reg("L")));

                // Special LD
                register(grouped, type, 0x02, "LD", 1, cycles(8), false, operands(mem("BC"), reg("A")));
                register(grouped, type, 0x12, "LD", 1, cycles(8), false, operands(mem("DE"), reg("A")));
                register(grouped, type, 0x22, "LD", 1, cycles(8), false, operands(mem("HL+"), reg("A")));
                register(grouped, type, 0x32, "LD", 1, cycles(8), false, operands(mem("HL-"), reg("A")));
                register(grouped, type, 0x0A, "LD", 1, cycles(8), false, operands(reg("A"), mem("BC")));
                register(grouped, type, 0x1A, "LD", 1, cycles(8), false, operands(reg("A"), mem("DE")));
                register(grouped, type, 0x2A, "LD", 1, cycles(8), false, operands(reg("A"), mem("HL+")));
                register(grouped, type, 0x3A, "LD", 1, cycles(8), false, operands(reg("A"), mem("HL-")));

                // LDH
                register(grouped, type, 0xE0, "LDH", 2, cycles(12), true, operands(mem("a8"), reg("A")));
                register(grouped, type, 0xF0, "LDH", 2, cycles(12), true, operands(reg("A"), mem("a8")));
                register(grouped, type, 0xE2, "LDH", 1, cycles(8), false, operands(mem("C"), reg("A")));
                register(grouped, type, 0xF2, "LDH", 1, cycles(8), false, operands(reg("A"), mem("C")));

                // LD A,(a16) / (a16),A / (a16),SP
                register(grouped, type, 0xFA, "LD", 3, cycles(16), true, operands(reg("A"), mem("a16")));
                register(grouped, type, 0xEA, "LD", 3, cycles(16), true, operands(mem("a16"), reg("A")));
                register(grouped, type, 0x08, "LD", 3, cycles(20), true, operands(mem("a16"), reg("SP")));

                // PUSH / POP
                register(grouped, type, 0xC5, "PUSH", 1, cycles(16), false, operands(reg("BC")));
                register(grouped, type, 0xD5, "PUSH", 1, cycles(16), false, operands(reg("DE")));
                register(grouped, type, 0xE5, "PUSH", 1, cycles(16), false, operands(reg("HL")));
                register(grouped, type, 0xF5, "PUSH", 1, cycles(16), false, operands(reg("AF")));
                register(grouped, type, 0xC1, "POP", 1, cycles(12), false, operands(reg("BC")));
                register(grouped, type, 0xD1, "POP", 1, cycles(12), false, operands(reg("DE")));
                register(grouped, type, 0xE1, "POP", 1, cycles(12), false, operands(reg("HL")));
                register(grouped, type, 0xF1, "POP", 1, cycles(12), false, operands(reg("AF")));

                // 16-bit helpers
                register(grouped, type, 0xF8, "LD", 2, cycles(12), true, operands(reg("HL"), imm("SP+r8")));
                register(grouped, type, 0xF9, "LD", 1, cycles(8), false, operands(reg("SP"), reg("HL")));
        }

        private static void registerArithmetic(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
                OperationType type = OperationType.ARITHMETIC;
                String[] targets = { "B", "C", "D", "E", "H", "L", "HL", "A" };
                boolean[] isRegister = { true, true, true, true, true, true, false, true };

                // ADD A, x
                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0x80 + i, "ADD", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(reg("A"), operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xC6, "ADD", 2, cycles(8), true, operands(reg("A"), imm("d8")));

                // ADC A, x
                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0x88 + i, "ADC", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(reg("A"), operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xCE, "ADC", 2, cycles(8), true, operands(reg("A"), imm("d8")));

                // SUB A, x (dest A is implied; we store only source in executor)
                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0x90 + i, "SUB", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xD6, "SUB", 2, cycles(8), true, operands(imm("d8")));

                // SBC A, x
                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0x98 + i, "SBC", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xDE, "SBC", 2, cycles(8), true, operands(imm("d8")));

                // ADD HL, rr
                register(grouped, type, 0x09, "ADD", 1, cycles(8), false, operands(reg("HL"), reg("BC")));
                register(grouped, type, 0x19, "ADD", 1, cycles(8), false, operands(reg("HL"), reg("DE")));
                register(grouped, type, 0x29, "ADD", 1, cycles(8), false, operands(reg("HL"), reg("HL")));
                register(grouped, type, 0x39, "ADD", 1, cycles(8), false, operands(reg("HL"), reg("SP")));

                // ADD SP, r8
                register(grouped, type, 0xE8, "ADD", 2, cycles(16), true, operands(reg("SP"), imm("r8")));
        }

        private static void registerLogic(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
                OperationType type = OperationType.LOGIC;
                String[] targets = { "B", "C", "D", "E", "H", "L", "HL", "A" };
                boolean[] isRegister = { true, true, true, true, true, true, false, true };

                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0xA0 + i, "AND", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xE6, "AND", 2, cycles(8), true, operands(imm("d8")));

                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0xA8 + i, "XOR", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xEE, "XOR", 2, cycles(8), true, operands(imm("d8")));

                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0xB0 + i, "OR", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xF6, "OR", 2, cycles(8), true, operands(imm("d8")));

                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0xB8 + i, "CP", 1, cycles(isRegister[i] ? 4 : 8), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }
                register(grouped, type, 0xFE, "CP", 2, cycles(8), true, operands(imm("d8")));
        }

        private static void registerIncDec(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
                OperationType type = OperationType.INC_DEC;
                String[] targets = { "B", "C", "D", "E", "H", "L", "HL", "A" };
                boolean[] isRegister = { true, true, true, true, true, true, false, true };
                int[] incOps = { 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C };
                int[] decOps = { 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D };

                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, incOps[i], "INC", 1, cycles(isRegister[i] ? 4 : 12), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, decOps[i], "DEC", 1, cycles(isRegister[i] ? 4 : 12), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }

                // 16-bit INC/DEC (flags unaffected)
                register(grouped, type, 0x03, "INC", 1, cycles(8), false, operands(reg("BC")));
                register(grouped, type, 0x13, "INC", 1, cycles(8), false, operands(reg("DE")));
                register(grouped, type, 0x23, "INC", 1, cycles(8), false, operands(reg("HL")));
                register(grouped, type, 0x33, "INC", 1, cycles(8), false, operands(reg("SP")));

                register(grouped, type, 0x0B, "DEC", 1, cycles(8), false, operands(reg("BC")));
                register(grouped, type, 0x1B, "DEC", 1, cycles(8), false, operands(reg("DE")));
                register(grouped, type, 0x2B, "DEC", 1, cycles(8), false, operands(reg("HL")));
                register(grouped, type, 0x3B, "DEC", 1, cycles(8), false, operands(reg("SP")));
        }

        private static void registerStack(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
                // Kept for structure; PUSH/POP declared under LD group per official tables.
        }

        private static void registerBitOperations(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
                OperationType type = OperationType.BIT_MANIPULATION;
                String[] targets = { "B", "C", "D", "E", "H", "L", "HL", "A" };
                boolean[] isRegister = { true, true, true, true, true, true, false, true };

                // Rotates/Shifts/SWAP (CB space 0x00..0x3F)
                for (int i = 0; i < targets.length; i++) {
                        register(grouped, type, 0x00 + i, "RLC", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x08 + i, "RRC", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x10 + i, "RL", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x18 + i, "RR", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x20 + i, "SLA", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x28 + i, "SRA", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x30 + i, "SWAP", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                        register(grouped, type, 0x38 + i, "SRL", 2, cycles(isRegister[i] ? 8 : 16), false,
                                        operands(operandForTarget(targets[i], isRegister[i])));
                }

                // BIT/RES/SET (CB space 0x40..0xFF)
                for (int bit = 0; bit < 8; bit++) {
                        for (int i = 0; i < targets.length; i++) {
                                int base = bit * 8 + i;
                                register(grouped, type, 0x40 + base, "BIT", 2, cycles(isRegister[i] ? 8 : 12), false,
                                                operands(bitOperand(bit), operandForTarget(targets[i], isRegister[i])));
                                register(grouped, type, 0x80 + base, "RES", 2, cycles(isRegister[i] ? 8 : 16), false,
                                                operands(bitOperand(bit), operandForTarget(targets[i], isRegister[i])));
                                register(grouped, type, 0xC0 + base, "SET", 2, cycles(isRegister[i] ? 8 : 16), false,
                                                operands(bitOperand(bit), operandForTarget(targets[i], isRegister[i])));
                        }
                }
        }

        /* ---------- Registration helpers ---------- */

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
                Operation op = new Operation(null, mnemonic, bytes, cycles, immediate, Map.of(),
                                new ArrayList<>(operands));
                op.setType(type);
                grouped.get(type).put(opcode & 0xFF, op);
        }

        private static List<Integer> cycles(int... values) {
                List<Integer> out = new ArrayList<>(values.length);
                for (int v : values)
                        out.add(v);
                return out;
        }

        @SafeVarargs
        private static List<Map<String, Object>> operands(Map<String, Object>... ops) {
                List<Map<String, Object>> out = new ArrayList<>(ops.length);
                for (Map<String, Object> m : ops)
                        out.add(m);
                return out;
        }

        private static Map<String, Object> reg(String name) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", name);
                m.put("immediate", false);
                m.put("memory", false);
                return m;
        }

        private static Map<String, Object> mem(String alias) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", alias);
                m.put("immediate", false);
                m.put("memory", true);
                return m;
        }

        private static Map<String, Object> imm(String alias) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", alias);
                m.put("immediate", true);
                return m;
        }

        private static Map<String, Object> operandForTarget(String name, boolean isRegister) {
                return isRegister ? reg(name) : mem(name);
        }

        private static Map<String, Object> bitOperand(int bit) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", Integer.toString(bit));
                m.put("immediate", true);
                return m;
        }
}
