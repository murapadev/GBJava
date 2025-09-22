package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class OperationsLoader {
    private final Map<Integer, Operation> operations;
    private final Map<Integer, Operation> cbOperations;
    private final EnumMap<OperationType, Map<Integer, Operation>> groupedOperations;
    private final EnumMap<OperationType, Map<Integer, Operation>> groupedCbOperations;
    private final CPU cpu;
    private final Interruptions interruptions;

    public OperationsLoader() {
        this(null, null);
    }

    public OperationsLoader(Interruptions interruptions) {
        this(null, null);
    }

    public OperationsLoader(Interruptions interruptions, CPU cpu) {
        this.operations = new HashMap<>();
        this.cbOperations = new HashMap<>();
        this.groupedOperations = new EnumMap<>(OperationType.class);
        this.groupedCbOperations = new EnumMap<>(OperationType.class);
        this.cpu = cpu;
        this.interruptions = interruptions;
        loadOperations();
    }

    private void loadOperations() {
        EnumMap<OperationType, Map<Integer, Operation>> unprefixed = OperationDefinitions.buildUnprefixedTable();
        EnumMap<OperationType, Map<Integer, Operation>> cbprefixed = OperationDefinitions.buildCbPrefixedTable();

        registerGroup(unprefixed, operations, groupedOperations);
        registerGroup(cbprefixed, cbOperations, groupedCbOperations);
    }

    private void registerGroup(EnumMap<OperationType, Map<Integer, Operation>> source,
            Map<Integer, Operation> flatTarget,
            EnumMap<OperationType, Map<Integer, Operation>> groupedTarget) {
        for (Map.Entry<OperationType, Map<Integer, Operation>> entry : source.entrySet()) {
            OperationType type = entry.getKey();
            Map<Integer, Operation> bucket = groupedTarget.computeIfAbsent(type, ignored -> new HashMap<>());

            for (Map.Entry<Integer, Operation> opEntry : entry.getValue().entrySet()) {
                Operation operation = opEntry.getValue();
                OperationExecutor executor = createExecutorForOperation(operation);
                if (executor == null) {
                    // Leave opcode unregistered to avoid executing without behaviour.
                    continue;
                }
                operation.setExecutor(executor);
                int opcode = opEntry.getKey() & 0xFF;
                flatTarget.put(opcode, operation);
                bucket.put(opcode, operation);
            }
        }
    }

    private OperationExecutor createExecutorForOperation(Operation operation) {
        String mnemonic = operation.getMnemonic();

        // Try arithmetic operations first
        OperationExecutor executor = createArithmeticExecutor(operation);
        if (executor != null)
            return executor;

        // Handle other operation types
        if ("NOP".equals(mnemonic)) {
            return (registers, memory, operands) -> {
                // No operation - intentionally left blank
            };
        }

        if ("RET".equals(mnemonic)) {
            return createRetExecutor();
        }

        if ("RETI".equals(mnemonic)) {
            return createRetiExecutor();
        }

        if ("LD".equals(mnemonic)) {
            return createLdExecutor();
        }

        if ("LDH".equals(mnemonic)) {
            return createLdhExecutor();
        }

        if ("JP".equals(mnemonic)) {
            return createJpExecutor();
        }

        if ("SET".equals(mnemonic)) {
            return createSetExecutor();
        }

        if ("RES".equals(mnemonic)) {
            return createResExecutor();
        }

        if ("BIT".equals(mnemonic)) {
            return createBitExecutor();
        }

        if ("PUSH".equals(mnemonic)) {
            return createPushExecutor();
        }

        if ("POP".equals(mnemonic)) {
            return createPopExecutor();
        }

        if ("RLCA".equals(mnemonic)) {
            return createRlcaExecutor();
        }

        if ("RRCA".equals(mnemonic)) {
            return createRrcaExecutor();
        }

        if ("RLA".equals(mnemonic)) {
            return createRlaExecutor();
        }

        if ("RRA".equals(mnemonic)) {
            return createRraExecutor();
        }

        if ("JR".equals(mnemonic)) {
            return createJrExecutor();
        }

        if ("CALL".equals(mnemonic)) {
            return createCallExecutor();
        }

        if ("DI".equals(mnemonic)) {
            return (registers, memory, operands) -> {
                // DI immediately disables interrupts
                if (cpu != null) {
                    cpu.setIme(false);
                }
            };
        }

        if ("EI".equals(mnemonic)) {
            return (registers, memory, operands) -> {
                // EI enables interrupts after the next instruction executes
                if (cpu != null) {
                    cpu.setImePending(true);
                }
            };
        }

        if ("STOP".equals(mnemonic)) {
            return (registers, memory, operands) -> {
                // STOP instruction - consume second byte (should be 0x00) and handle speed
                // switching
                byte secondByte = getImmediateByte(registers, memory);
                if (secondByte != 0x00) {
                    System.err.println(String.format("WARNING: STOP instruction with unexpected second byte: 0x%02X",
                            secondByte & 0xFF));
                }

                // Handle speed switching (moved from CPU special handling)
                if (cpu != null && cpu.isPrepareSpeedSwitch()) {
                    // Switch speed mode
                    cpu.setDoubleSpeedMode(!cpu.isDoubleSpeedMode());
                    cpu.setPrepareSpeedSwitch(false); // Clear prepare bit

                    // Reset DIV register when switching speeds
                    memory.writeByte(0xFF04, (byte) 0x00);

                    // Adjust timers for new speed mode
                    // Reset TIMA to TMA value when switching speeds
                    int tma = memory.readByte(0xFF06) & 0xFF;
                    memory.writeByte(0xFF05, (byte) tma);
                }
            };
        }

        if ("DAA".equals(mnemonic)) {
            return createDaaExecutor();
        }

        if ("CPL".equals(mnemonic)) {
            return createCplExecutor();
        }

        if ("SCF".equals(mnemonic)) {
            return createScfExecutor();
        }

        if ("CCF".equals(mnemonic)) {
            return createCcfExecutor();
        }

        if ("RLC".equals(mnemonic)) {
            return createRlcExecutor();
        }

        if ("RRC".equals(mnemonic)) {
            return createRrcExecutor();
        }

        if ("RL".equals(mnemonic)) {
            return createRlExecutor();
        }

        if ("RR".equals(mnemonic)) {
            return createRrExecutor();
        }

        if ("SLA".equals(mnemonic)) {
            return createSlaExecutor();
        }

        if ("SRA".equals(mnemonic)) {
            return createSraExecutor();
        }

        if ("SWAP".equals(mnemonic)) {
            return createSwapExecutor();
        }

        if ("SRL".equals(mnemonic)) {
            return createSrlExecutor();
        }

        if ("HALT".equals(mnemonic)) {
            return (registers, memory, operands) -> {
                if (!cpu.isIme() && interruptions.hasPendingInterrupt()) {
                    cpu.setHaltBugTriggered(true);
                }
                cpu.setHalted(true);
            };
        }

        if ("RST".equals(mnemonic)) {
            return createRstExecutor();
        }

        if ("INVALID".equals(mnemonic)) {
            return (registers, memory, operands) -> {
                // INVALID opcodes hard-lock the CPU - we'll just warn and advance PC
                System.err.println(String.format("WARNING: Invalid opcode executed at PC=0x%04X",
                        (int) registers.getPC()));
                // Just advance PC to prevent infinite loop
                registers.incrementPC();
            };
        }

        // Default executor for unimplemented operations
        return (registers, memory, operands) -> {
            // FIXME: Better handling of unimplemented operations
            // Currently just consumes bytes but doesn't advance properly
            System.err.println(String.format("WARNING: Unimplemented operation: %s at PC=0x%04X",
                    operation.getMnemonic(), (int) registers.getPC()));

            // Default executor - just consume the correct number of bytes
            if (operation.getBytes() > 1) {
                // Skip immediate operands
                for (int i = 0; i < operation.getBytes() - 1; i++) {
                    registers.incrementPC();
                }
            }
            // For unimplemented operations, at least advance PC by 1 if no operands
            if (operation.getBytes() == 1) {
                registers.incrementPC();
            }
        };
    }

    public Operation getOperation(int opcode) {
        return operations.get(opcode);
    }

    public Operation getCbOperation(int opcode) {
        return cbOperations.get(opcode);
    }

    public Map<Integer, Operation> getOperations(OperationType type) {
        Map<Integer, Operation> bucket = groupedOperations.get(type);
        return bucket == null ? Map.of() : Map.copyOf(bucket);
    }

    public Map<Integer, Operation> getCbOperations(OperationType type) {
        Map<Integer, Operation> bucket = groupedCbOperations.get(type);
        return bucket == null ? Map.of() : Map.copyOf(bucket);
    }

    // Helper methods for common operation patterns
    private void setFlags(Registers registers, boolean z, boolean n, boolean h, boolean c) {
        byte flags = registers.getRegister("F");
        flags = (byte) ((flags & 0x0F) | (z ? 0x80 : 0) | (n ? 0x40 : 0) | (h ? 0x20 : 0) | (c ? 0x10 : 0));
        registers.setRegister("F", flags);
    }

    private void setZFlag(Registers registers, boolean z) {
        byte flags = registers.getRegister("F");
        flags = (byte) ((flags & 0x7F) | (z ? 0x80 : 0));
        registers.setRegister("F", flags);
    }

    private void setNFlag(Registers registers, boolean n) {
        byte flags = registers.getRegister("F");
        flags = (byte) ((flags & 0xBF) | (n ? 0x40 : 0));
        registers.setRegister("F", flags);
    }

    private void setHFlag(Registers registers, boolean h) {
        byte flags = registers.getRegister("F");
        flags = (byte) ((flags & 0xDF) | (h ? 0x20 : 0));
        registers.setRegister("F", flags);
    }

    private void setCFlag(Registers registers, boolean c) {
        byte flags = registers.getRegister("F");
        flags = (byte) ((flags & 0xEF) | (c ? 0x10 : 0));
        registers.setRegister("F", flags);
    }

    private byte getImmediateByte(Registers registers, Memory memory) {
        byte value = (byte) memory.readByte(registers.getPC());
        registers.incrementPC();
        return value;
    }

    private char getImmediateChar(Registers registers, Memory memory) {
        char value = (char) memory.readChar(registers.getPC());
        registers.incrementPC();
        registers.incrementPC();
        return value;
    }

    private int readMemoryOrRegister(Registers registers, Memory memory, Map<String, Object> operand) {
        String name = (String) operand.get("name");
        Boolean isImmediate = (Boolean) operand.get("immediate");
        Boolean isMemory = (Boolean) operand.get("memory");

        // Handle immediate values
        if (isImmediate != null && isImmediate) {
            if ("d8".equals(name)) {
                return getImmediateByte(registers, memory) & 0xFF;
            } else if ("d16".equals(name)) {
                return getImmediateChar(registers, memory) & 0xFFFF;
            } else if ("a8".equals(name)) {
                return getImmediateByte(registers, memory) & 0xFF;
            } else if ("a16".equals(name)) {
                return getImmediateChar(registers, memory) & 0xFFFF;
            } else if ("r8".equals(name)) {
                return getImmediateByte(registers, memory);
            }
        }

        // Handle memory access through registers
        if (isMemory != null && isMemory) {
            if ("HL".equals(name)) {
                return memory.readByte(registers.getHL()) & 0xFF;
            }
            if ("HL+".equals(name)) {
                int value = memory.readByte(registers.getHL()) & 0xFF;
                registers.setHL(registers.getHL() + 1);
                return value;
            }
            if ("HL-".equals(name)) {
                int value = memory.readByte(registers.getHL()) & 0xFF;
                registers.setHL(registers.getHL() - 1);
                return value;
            }
            if ("BC".equals(name)) {
                return memory.readByte(registers.getBC()) & 0xFF;
            }
            if ("DE".equals(name)) {
                return memory.readByte(registers.getDE()) & 0xFF;
            }
            // Add other memory access patterns as needed
        }

        // Handle 16-bit register pairs - these should return the register pair value
        if (is16BitRegister(name) && (isMemory == null || !isMemory)) {
            return switch (name) {
                case "BC" -> registers.getBC() & 0xFFFF;
                case "DE" -> registers.getDE() & 0xFFFF;
                case "HL" -> registers.getHL() & 0xFFFF;
                case "AF" -> registers.getAF() & 0xFFFF;
                case "SP" -> registers.getSP() & 0xFFFF;
                default -> 0;
            };
        }

        // Fallback: if HL and not immediate, assume memory access
        else if ("HL".equals(name) && isImmediate != null && !isImmediate) {
            return memory.readByte(registers.getHL()) & 0xFF;
        }

        // Handle single 8-bit register access only (not 16-bit register pairs)
        if (!is16BitRegister(name) && !"a8".equals(name) && !"a16".equals(name) && !"d8".equals(name)
                && !"d16".equals(name) && !"r8".equals(name)) {
            return registers.getRegister(name) & 0xFF;
        }

        // Default fallback for unhandled cases
        System.err.println("Unhandled operand in readMemoryOrRegister: " + name + " immediate=" + isImmediate
                + " memory=" + isMemory);
        return 0;
    }

    private void writeMemoryOrRegister(Registers registers, Memory memory, Map<String, Object> operand, int value) {
        String name = (String) operand.get("name");
        Boolean isImmediate = (Boolean) operand.get("immediate");
        Boolean isMemory = (Boolean) operand.get("memory");

        // Handle immediate values - these shouldn't be written to, but just in case
        if (isImmediate != null && isImmediate) {
            // Immediates are read-only, do nothing
            return;
        }

        // Handle memory access through registers
        if (isMemory != null && isMemory) {
            if ("HL".equals(name)) {
                memory.writeByte(registers.getHL(), (byte) value);
                return;
            }
            if ("HL+".equals(name)) {
                memory.writeByte(registers.getHL(), (byte) value);
                registers.setHL(registers.getHL() + 1);
                return;
            }
            if ("HL-".equals(name)) {
                memory.writeByte(registers.getHL(), (byte) value);
                registers.setHL(registers.getHL() - 1);
                return;
            }
            if ("BC".equals(name)) {
                memory.writeByte(registers.getBC(), (byte) value);
                return;
            }
            if ("DE".equals(name)) {
                memory.writeByte(registers.getDE(), (byte) value);
                return;
            }
            if ("a16".equals(name)) {
                // For a16 memory writes, the address was already consumed during execution
                // This should have been handled by the operation executor
                System.err.println("Warning: a16 memory write should be handled by operation executor");
                return;
            }
            // Add other memory access patterns as needed
        }
        // Fallback: if HL and not immediate, assume memory access
        else if ("HL".equals(name) && isImmediate != null && !isImmediate) {
            memory.writeByte(registers.getHL(), (byte) value);
            return;
        }

        // Handle 16-bit register pairs
        if (is16BitRegister(name)) {
            set16BitRegister(registers, name, value & 0xFFFF);
            return;
        }

        // Handle single 8-bit register access only (not 16-bit register pairs or
        // immediates)
        if (!is16BitRegister(name) && !"a8".equals(name) && !"a16".equals(name) && !"d8".equals(name)
                && !"d16".equals(name) && !"r8".equals(name)) {
            registers.setRegister(name, (byte) value);
        } else {
            System.err.println("Unhandled operand in writeMemoryOrRegister: " + name + " immediate=" + isImmediate
                    + " memory=" + isMemory);
        }
    }

    // Executor factories for different operation categories
    private OperationExecutor createArithmeticExecutor(Operation operation) {
        String mnemonic = operation.getMnemonic();

        if ("ADD".equals(mnemonic)) {
            return createAddExecutor();
        }
        if ("ADC".equals(mnemonic)) {
            return createAdcExecutor();
        }
        if ("SUB".equals(mnemonic)) {
            return createSubExecutor();
        }
        if ("SBC".equals(mnemonic)) {
            return createSbcExecutor();
        }
        if ("AND".equals(mnemonic)) {
            return createAndExecutor();
        }
        if ("OR".equals(mnemonic)) {
            return createOrExecutor();
        }
        if ("XOR".equals(mnemonic)) {
            return createXorExecutor();
        }
        if ("CP".equals(mnemonic)) {
            return createCpExecutor();
        }
        if ("INC".equals(mnemonic)) {
            return createIncExecutor();
        }
        if ("DEC".equals(mnemonic)) {
            return createDecExecutor();
        }

        return null;
    }

    private OperationExecutor createAddExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 2)
                return;

            Map<String, Object> destOp = operands.get(0);
            Map<String, Object> srcOp = operands.get(1);
            String destReg = (String) destOp.get("name");
            String srcReg = (String) srcOp.get("name");

            // Handle 16-bit ADD HL, ss
            if ("HL".equals(destReg) && destOp.get("immediate") != null && !(Boolean) destOp.get("immediate") &&
                    srcOp.get("immediate") != null && !(Boolean) srcOp.get("immediate") &&
                    ("BC".equals(srcReg) || "DE".equals(srcReg) || "HL".equals(srcReg) || "SP".equals(srcReg))) {

                int hl = registers.getHL();
                int hlVal = hl & 0xFFFF;
                int srcVal = switch (srcReg) {
                    case "BC" -> registers.getBC() & 0xFFFF;
                    case "DE" -> registers.getDE() & 0xFFFF;
                    case "HL" -> registers.getHL() & 0xFFFF;
                    case "SP" -> registers.getSP() & 0xFFFF;
                    default -> 0;
                };

                int sum = hlVal + srcVal;
                char result = (char) (sum & 0xFFFF);
                registers.setHL(result);

                // Flags: Z unaffected, N=0, H: carry from bit 11, C: carry from bit 15
                setHFlag(registers, ((hlVal & 0x0FFF) + (srcVal & 0x0FFF)) > 0x0FFF);
                setCFlag(registers, sum > 0xFFFF);
                return;
            }

            // Handle ADD SP, r8
            if ("SP".equals(destReg) && "r8".equals(srcReg)) {
                byte offset = getImmediateByte(registers, memory);
                int spVal = registers.getSP() & 0xFFFF;
                int sOffset = (byte) offset; // sign-extend
                int result = (spVal + sOffset) & 0xFFFF;

                // Flags: Z=0, N=0, H and C from lower 8/4 bits of addition
                setFlags(registers, false, false,
                        ((spVal & 0x0F) + (sOffset & 0x0F)) > 0x0F,
                        ((spVal & 0xFF) + (sOffset & 0xFF)) > 0xFF);
                registers.setSP((char) result);
                return;
            }

            // Default 8-bit ADD r, r/d8/(HL)
            byte destValue = registers.getRegister(destReg);
            byte srcValue;

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                srcValue = (byte) readMemoryOrRegister(registers, memory, srcOp);
            }

            int result = (destValue & 0xFF) + (srcValue & 0xFF);
            byte resultByte = (byte) (result & 0xFF);

            registers.setRegister(destReg, resultByte);

            // Set flags
            setFlags(registers, resultByte == 0, false,
                    ((destValue & 0x0F) + (srcValue & 0x0F)) > 0x0F,
                    result > 0xFF);
        };
    }

    private OperationExecutor createAdcExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 2)
                return;

            Map<String, Object> destOp = operands.get(0);
            Map<String, Object> srcOp = operands.get(1);
            String destReg = (String) destOp.get("name");
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister(destReg);
            byte srcValue;
            byte carry = (byte) ((registers.getRegister("F") & 0x10) >> 4);

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else if ("HL".equals(srcReg)) {
                srcValue = (byte) memory.readByte(registers.getHL());
            } else {
                srcValue = registers.getRegister(srcReg);
            }

            int result = (destValue & 0xFF) + (srcValue & 0xFF) + carry;
            byte resultByte = (byte) (result & 0xFF);

            registers.setRegister(destReg, resultByte);

            // Set flags
            setFlags(registers, resultByte == 0, false,
                    ((destValue & 0x0F) + (srcValue & 0x0F) + carry) > 0x0F,
                    result > 0xFF);
        };
    }

    private OperationExecutor createSubExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> srcOp = operands.get(0);
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister("A");
            byte srcValue;

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                srcValue = (byte) readMemoryOrRegister(registers, memory, srcOp);
            }

            int result = (destValue & 0xFF) - (srcValue & 0xFF);
            byte resultByte = (byte) (result & 0xFF);

            registers.setRegister("A", resultByte);

            // Set flags
            setFlags(registers, resultByte == 0, true,
                    ((destValue & 0x0F) - (srcValue & 0x0F)) < 0,
                    result < 0);
        };
    }

    private OperationExecutor createSbcExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> srcOp = operands.get(0);
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister("A");
            byte srcValue;
            byte carry = (byte) ((registers.getRegister("F") & 0x10) >> 4);

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                srcValue = (byte) readMemoryOrRegister(registers, memory, srcOp);
            }

            int result = (destValue & 0xFF) - (srcValue & 0xFF) - carry;
            byte resultByte = (byte) (result & 0xFF);

            registers.setRegister("A", resultByte);

            // Set flags
            setFlags(registers, resultByte == 0, true,
                    ((destValue & 0x0F) - (srcValue & 0x0F) - carry) < 0,
                    result < 0);
        };
    }

    private OperationExecutor createAndExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> srcOp = operands.get(0);
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister("A");
            byte srcValue;

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                int temp = readMemoryOrRegister(registers, memory, srcOp);
                srcValue = (byte) (temp & 0xFF);
            }

            byte result = (byte) (destValue & srcValue);
            registers.setRegister("A", result);

            // Set flags: Z, N=0, H=1, C=0
            setFlags(registers, result == 0, false, true, false);
        };
    }

    private OperationExecutor createOrExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> srcOp = operands.get(0);
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister("A");
            byte srcValue;

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                srcValue = (byte) readMemoryOrRegister(registers, memory, srcOp);
            }

            byte result = (byte) (destValue | srcValue);
            registers.setRegister("A", result);

            // Set flags: Z, N=0, H=0, C=0
            setFlags(registers, result == 0, false, false, false);
        };
    }

    private OperationExecutor createXorExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> srcOp = operands.get(0);
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister("A");
            byte srcValue;

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                srcValue = (byte) readMemoryOrRegister(registers, memory, srcOp);
            }

            byte result = (byte) (destValue ^ srcValue);
            registers.setRegister("A", result);

            // Set flags: Z, N=0, H=0, C=0
            setFlags(registers, result == 0, false, false, false);
        };
    }

    private OperationExecutor createCpExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> srcOp = operands.get(0);
            String srcReg = (String) srcOp.get("name");

            byte destValue = registers.getRegister("A");
            byte srcValue;

            if ("d8".equals(srcReg)) {
                srcValue = getImmediateByte(registers, memory);
            } else {
                srcValue = (byte) readMemoryOrRegister(registers, memory, srcOp);
            }

            int result = (destValue & 0xFF) - (srcValue & 0xFF);
            byte resultByte = (byte) (result & 0xFF);

            // Set flags (don't modify A register)
            setFlags(registers, resultByte == 0, true,
                    ((destValue & 0x0F) - (srcValue & 0x0F)) < 0,
                    result < 0);
        };
    }

    private OperationExecutor createIncExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> destOp = operands.get(0);
            String destReg = (String) destOp.get("name");
            Boolean isMemory = (Boolean) destOp.get("memory");
            Boolean isImmediate = (Boolean) destOp.get("immediate");

            byte value;
            // INC (HL) - check for memory access (either explicit memory flag or HL
            // fallback)
            if ((isMemory != null && isMemory && "HL".equals(destReg)) ||
                    (isMemory == null && "HL".equals(destReg) && isImmediate != null && !isImmediate)) {
                value = (byte) memory.readByte(registers.getHL());
                int result = (value & 0xFF) + 1;
                byte resultByte = (byte) (result & 0xFF);
                memory.writeByte(registers.getHL(), resultByte);

                setZFlag(registers, resultByte == 0);
                setNFlag(registers, false);
                setHFlag(registers, ((value & 0x0F) + 1) > 0x0F);
            }
            // INC 16-bit register pair
            else if (is16BitRegister(destReg)) {
                int val;
                switch (destReg) {
                    case "BC": {
                        int bc = registers.getBC() & 0xFFFF;
                        val = (bc + 1) & 0xFFFF;
                        break;
                    }
                    case "DE": {
                        int de = registers.getDE() & 0xFFFF;
                        val = (de + 1) & 0xFFFF;
                        break;
                    }
                    case "HL": {
                        int hl = registers.getHL() & 0xFFFF;
                        val = (hl + 1) & 0xFFFF;
                        break;
                    }
                    case "SP": {
                        val = (registers.getSP() + 1) & 0xFFFF;
                        break;
                    }
                    default:
                        val = 0;
                        break;
                }
                switch (destReg) {
                    case "BC" -> registers.setBC(val);
                    case "DE" -> registers.setDE(val);
                    case "HL" -> registers.setHL(val);
                    case "SP" -> registers.setSP(val);
                }
                // Flags unaffected
            }
            // INC r (8-bit)
            else {
                value = registers.getRegister(destReg);
                int result = (value & 0xFF) + 1;
                byte resultByte = (byte) (result & 0xFF);
                registers.setRegister(destReg, resultByte);

                setZFlag(registers, resultByte == 0);
                setNFlag(registers, false);
                setHFlag(registers, ((value & 0x0F) + 1) > 0x0F);
            }
        };
    }

    private OperationExecutor createDecExecutor() {
        return (registers, memory, operands) -> {
            if (operands == null || operands.size() < 1)
                return;

            Map<String, Object> destOp = operands.get(0);
            String destReg = (String) destOp.get("name");
            Boolean isMemory = (Boolean) destOp.get("memory");
            Boolean isImmediate = (Boolean) destOp.get("immediate");

            byte value;
            // DEC (HL) - check for memory access (either explicit memory flag or HL
            // fallback)
            if ((isMemory != null && isMemory && "HL".equals(destReg)) ||
                    (isMemory == null && "HL".equals(destReg) && isImmediate != null && !isImmediate)) {
                value = (byte) memory.readByte(registers.getHL());
                int result = (value & 0xff) - 1;
                byte resultByte = (byte) (result & 0xff);
                memory.writeByte(registers.getHL(), resultByte);

                setZFlag(registers, resultByte == 0);
                setNFlag(registers, true);
                setHFlag(registers, ((value & 0x0F) - 1) < 0);
            }
            // DEC 16-bit register pair
            else if (is16BitRegister(destReg)) {
                int val;
                switch (destReg) {
                    case "BC": {
                        int bc = registers.getBC() & 0xFFFF;
                        val = (bc - 1) & 0xFFFF;
                        break;
                    }
                    case "DE": {
                        int de = registers.getDE() & 0xFFFF;
                        val = (de - 1) & 0xFFFF;
                        break;
                    }
                    case "HL": {
                        int hl = registers.getHL() & 0xFFFF;
                        val = (hl - 1) & 0xFFFF;
                        break;
                    }
                    case "SP": {
                        val = (registers.getSP() - 1) & 0xFFFF;
                        break;
                    }
                    default:
                        val = 0;
                        break;
                }
                switch (destReg) {
                    case "BC" -> registers.setBC(val);
                    case "DE" -> registers.setDE(val);
                    case "HL" -> registers.setHL(val);
                    case "SP" -> registers.setSP(val);
                }
                // Flags unaffected
            }
            // DEC r (8-bit)
            else {
                value = registers.getRegister(destReg);
                int result = (value & 0xFF) - 1;
                byte resultByte = (byte) (result & 0xFF);
                registers.setRegister(destReg, resultByte);

                setZFlag(registers, resultByte == 0);
                setNFlag(registers, true);
                setHFlag(registers, ((value & 0x0F) - 1) < 0);
            }
        };
    }

    private OperationExecutor createLdExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 2) {
                Map<String, Object> dest = operands.get(0);
                Map<String, Object> src = operands.get(1);
                String destName = (String) dest.get("name");
                String srcName = (String) src.get("name");

                // Handle special cases first

                // LD (a16), SP - Store SP at immediate 16-bit address
                if ("a16".equals(destName) && "SP".equals(srcName)) {
                    int address = getImmediateChar(registers, memory);
                    memory.writeChar(address, registers.getSP());
                    return;
                }

                // LD (a16), A - Store A at immediate 16-bit address
                if ("a16".equals(destName) && "A".equals(srcName)) {
                    int address = getImmediateChar(registers, memory);
                    memory.writeByte(address, registers.getRegister("A"));
                    return;
                }

                // LD A, (a16) - Load A from immediate 16-bit address
                if ("A".equals(destName) && "a16".equals(srcName)) {
                    int address = getImmediateChar(registers, memory);
                    byte value = (byte) memory.readByte(address);
                    registers.setRegister("A", value);
                    return;
                }

                // LD HL, SP+r8 - Load HL with SP plus signed 8-bit immediate
                if ("HL".equals(destName) && "SP+r8".equals(srcName)) {
                    byte offset = getImmediateByte(registers, memory);
                    int spVal = registers.getSP() & 0xFFFF;
                    int sOffset = (byte) offset; // sign-extend
                    int result = (spVal + sOffset) & 0xFFFF;

                    // Flags: Z=0, N=0, H and C from lower 8/4 bits of addition
                    setFlags(registers, false, false,
                            ((spVal & 0x0F) + (sOffset & 0x0F)) > 0x0F,
                            ((spVal & 0xFF) + (sOffset & 0xFF)) > 0xFF);
                    registers.setHL((char) result);
                    return;
                }

                // Handle 16-bit loads (LD rr, d16)
                if (is16BitRegister(destName) && "d16".equals(srcName)) {
                    int value = getImmediateChar(registers, memory);
                    set16BitRegister(registers, destName, value);
                    return;
                }

                // Handle 8-bit loads
                int value = readMemoryOrRegister(registers, memory, src);
                writeMemoryOrRegister(registers, memory, dest, value);
            }
        };
    }

    private OperationExecutor createLdhExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 2) {
                Map<String, Object> dest = operands.get(0);
                Map<String, Object> src = operands.get(1);
                String destName = (String) dest.get("name");
                String srcName = (String) src.get("name");

                // LDH A, (a8) - Load from high memory
                if ("A".equals(destName) && "a8".equals(srcName)) {
                    byte offset = getImmediateByte(registers, memory);
                    int address = 0xFF00 + (offset & 0xFF);
                    byte value = (byte) memory.readByte(address);
                    registers.setRegister("A", value);
                }
                // LDH (a8), A - Store to high memory
                else if ("a8".equals(destName) && "A".equals(srcName)) {
                    byte offset = getImmediateByte(registers, memory);
                    int address = 0xFF00 + (offset & 0xFF);
                    byte value = registers.getRegister("A");
                    memory.writeByte(address, value);
                }
                // LDH A, (C) - Load from high memory using C register
                else if ("A".equals(destName) && "C".equals(srcName)) {
                    byte offset = registers.getRegister("C");
                    int address = 0xFF00 + (offset & 0xFF);
                    byte value = (byte) memory.readByte(address);
                    registers.setRegister("A", value);
                }
                // LDH (C), A - Store to high memory using C register
                else if ("C".equals(destName) && "A".equals(srcName)) {
                    byte offset = registers.getRegister("C");
                    int address = 0xFF00 + (offset & 0xFF);
                    byte value = registers.getRegister("A");
                    memory.writeByte(address, value);
                }
            }
        };
    }

    private OperationExecutor createJpExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() >= 1) {
                boolean condition = true;

                // Check for conditional jump
                if (operands.size() == 2) {
                    String conditionName = (String) operands.get(0).get("name");
                    condition = checkCondition(registers, conditionName);
                }
                // Always read immediate 16-bit address to advance PC correctly
                int address = readMemoryOrRegister(registers, memory, operands.get(operands.size() - 1));
                if (condition) {
                    registers.setPC(address);
                }
                if (cpu != null)
                    cpu.setLastConditionTaken(condition && operands.size() == 2);
            }
        };
    }

    private OperationExecutor createJrExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() >= 1) {
                boolean condition = true;

                // Check for conditional jump
                if (operands.size() == 2) {
                    String conditionName = (String) operands.get(0).get("name");
                    condition = checkCondition(registers, conditionName);
                }
                // Always fetch immediate signed offset
                int offset = getImmediateByte(registers, memory);
                if (condition) {
                    int newPC = registers.getPC() + (byte) offset;
                    registers.setPC(newPC);
                }
                if (cpu != null)
                    cpu.setLastConditionTaken(condition && operands.size() == 2);
            }
        };
    }

    private OperationExecutor createCallExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() >= 1) {
                boolean condition = true;
                int operandIndex = 0;

                // Check for conditional call
                if (operands.size() == 2) {
                    String conditionName = (String) operands.get(0).get("name");
                    condition = checkCondition(registers, conditionName);
                    operandIndex = 1;
                }
                // Always read immediate address (advances PC) then decide
                int address = readMemoryOrRegister(registers, memory, operands.get(operandIndex));
                if (condition) {
                    int returnAddress = registers.getPC();
                    registers.setSP(registers.getSP() - 2);
                    memory.writeChar(registers.getSP(), returnAddress);
                    registers.setPC(address);
                }
                if (cpu != null)
                    cpu.setLastConditionTaken(condition && operands.size() == 2);
            }
        };
    }

    private OperationExecutor createRetExecutor() {
        return (registers, memory, operands) -> {
            boolean condition = true;

            // Check for conditional return
            if (operands.size() == 1) {
                String conditionName = (String) operands.get(0).get("name");
                condition = checkCondition(registers, conditionName);
            }
            if (condition) {
                int returnAddress = memory.readChar(registers.getSP());
                registers.setSP(registers.getSP() + 2);
                registers.setPC(returnAddress);
            }
            if (cpu != null)
                cpu.setLastConditionTaken(condition && operands.size() == 1);
        };
    }

    private OperationExecutor createRetiExecutor() {
        return (registers, memory, operands) -> {
            // RETI - return from interrupt and enable interrupts immediately
            int returnAddress = memory.readChar(registers.getSP());
            registers.setSP(registers.getSP() + 2);
            registers.setPC(returnAddress);
            // Enable interrupts immediately (unlike EI which has delayed effect)
            if (cpu != null) {
                cpu.setIme(true);
            }
        };
    }

    private OperationExecutor createRstExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                String addressStr = (String) operands.get(0).get("name");
                int address = Integer.parseInt(addressStr.substring(0, addressStr.length() - 1), 16);
                int returnAddress = registers.getPC();
                registers.setSP(registers.getSP() - 2);
                memory.writeChar(registers.getSP(), returnAddress);
                registers.setPC(address);
            }
        };
    }

    private OperationExecutor createPushExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> operand = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, operand);
                registers.setSP(registers.getSP() - 2);
                memory.writeChar(registers.getSP(), value);
            }
        };
    }

    private OperationExecutor createPopExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                int value = memory.readChar(registers.getSP());
                registers.setSP(registers.getSP() + 2);
                writeMemoryOrRegister(registers, memory, operands.get(0), value);
            }
        };
    }

    private boolean checkCondition(Registers registers, String conditionName) {
        byte flags = registers.getRegister("F");
        switch (conditionName) {
            case "NZ":
                return (flags & 0x80) == 0;
            case "Z":
                return (flags & 0x80) != 0;
            case "NC":
                return (flags & 0x10) == 0;
            case "C":
                return (flags & 0x10) != 0;
            default:
                return true;
        }
    }

    private OperationExecutor createSetExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 2) {
                int bit = Integer.parseInt((String) operands.get(0).get("name"));
                Map<String, Object> target = operands.get(1);

                int value = readMemoryOrRegister(registers, memory, target);
                value |= (1 << bit);
                writeMemoryOrRegister(registers, memory, target, value);
            }
        };
    }

    private OperationExecutor createResExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 2) {
                int bit = Integer.parseInt((String) operands.get(0).get("name"));
                Map<String, Object> target = operands.get(1);

                int value = readMemoryOrRegister(registers, memory, target);
                value &= ~(1 << bit);
                writeMemoryOrRegister(registers, memory, target, value);
            }
        };
    }

    private OperationExecutor createBitExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 2) {
                int bit = Integer.parseInt((String) operands.get(0).get("name"));
                Map<String, Object> target = operands.get(1);

                int value = readMemoryOrRegister(registers, memory, target);
                boolean bitSet = (value & (1 << bit)) != 0;

                setZFlag(registers, !bitSet);
                setNFlag(registers, false);
                setHFlag(registers, true);
                // C flag unchanged
            }
        };
    }

    private OperationExecutor createRlcaExecutor() {
        return (registers, memory, operands) -> {
            int value = registers.getRegister("A") & 0xFF;
            int carry = (value & 0x80) >> 7;
            value = (value << 1) | carry;
            registers.setRegister("A", (byte) value);

            setZFlag(registers, false);
            setNFlag(registers, false);
            setHFlag(registers, false);
            setCFlag(registers, carry != 0);
        };
    }

    private OperationExecutor createRrcaExecutor() {
        return (registers, memory, operands) -> {
            int value = registers.getRegister("A") & 0xFF;
            int carry = value & 0x01;
            value = (value >> 1) | (carry << 7);
            registers.setRegister("A", (byte) value);

            setZFlag(registers, false);
            setNFlag(registers, false);
            setHFlag(registers, false);
            setCFlag(registers, carry != 0);
        };
    }

    private OperationExecutor createRlaExecutor() {
        return (registers, memory, operands) -> {
            int value = registers.getRegister("A") & 0xFF;
            int carryIn = (registers.getRegister("F") & 0x10) >> 4;
            int carryOut = (value & 0x80) >> 7;
            value = (value << 1) | carryIn;
            registers.setRegister("A", (byte) value);

            setZFlag(registers, false);
            setNFlag(registers, false);
            setHFlag(registers, false);
            setCFlag(registers, carryOut != 0);
        };
    }

    private OperationExecutor createRraExecutor() {
        return (registers, memory, operands) -> {
            int value = registers.getRegister("A") & 0xFF;
            int carryIn = (registers.getRegister("F") & 0x10) >> 4;
            int carryOut = value & 0x01;
            value = (value >> 1) | (carryIn << 7);
            registers.setRegister("A", (byte) value);

            setZFlag(registers, false);
            setNFlag(registers, false);
            setHFlag(registers, false);
            setCFlag(registers, carryOut != 0);
        };
    }

    private OperationExecutor createRlcExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carry = (value & 0x80) >> 7;
                value = (value << 1) | carry;
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carry != 0);
            }
        };
    }

    private OperationExecutor createRrcExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carry = value & 0x01;
                value = (value >> 1) | (carry << 7);
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carry != 0);
            }
        };
    }

    private OperationExecutor createRlExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carryIn = (registers.getRegister("F") & 0x10) >> 4;
                int carryOut = (value & 0x80) >> 7;
                value = (value << 1) | carryIn;
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carryOut != 0);
            }
        };
    }

    private OperationExecutor createRrExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carryIn = (registers.getRegister("F") & 0x10) >> 4;
                int carryOut = value & 0x01;
                value = (value >> 1) | (carryIn << 7);
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carryOut != 0);
            }
        };
    }

    private OperationExecutor createSlaExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carry = (value & 0x80) >> 7;
                value = (value << 1) & 0xFF;
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carry != 0);
            }
        };
    }

    private OperationExecutor createSraExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carry = value & 0x01;
                int msb = value & 0x80;
                value = (value >> 1) | msb;
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carry != 0);
            }
        };
    }

    private OperationExecutor createSrlExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                int carry = value & 0x01;
                value = value >> 1;
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, carry != 0);
            }
        };
    }

    private OperationExecutor createSwapExecutor() {
        return (registers, memory, operands) -> {
            if (operands.size() == 1) {
                Map<String, Object> target = operands.get(0);
                int value = readMemoryOrRegister(registers, memory, target);
                value = ((value & 0x0F) << 4) | ((value & 0xF0) >> 4);
                writeMemoryOrRegister(registers, memory, target, value);

                setZFlag(registers, value == 0);
                setNFlag(registers, false);
                setHFlag(registers, false);
                setCFlag(registers, false);
            }
        };
    }

    private OperationExecutor createDaaExecutor() {
        return (registers, memory, operands) -> {
            int a = registers.getRegister("A") & 0xFF;
            byte flags = registers.getRegister("F");
            boolean n = (flags & 0x40) != 0;
            boolean h = (flags & 0x20) != 0;
            boolean c = (flags & 0x10) != 0;

            if (!n) {
                if (c || a > 0x99) {
                    a = (a + 0x60) & 0xFF;
                    c = true;
                }
                if (h || (a & 0x0F) > 0x09) {
                    a = (a + 0x06) & 0xFF;
                }
            } else {
                if (c) {
                    a = (a - 0x60) & 0xFF;
                }
                if (h) {
                    a = (a - 0x06) & 0xFF;
                }
            }

            registers.setRegister("A", (byte) a);
            setZFlag(registers, a == 0);
            setHFlag(registers, false);
            setCFlag(registers, c);
        };
    }

    private OperationExecutor createCplExecutor() {
        return (registers, memory, operands) -> {
            int a = registers.getRegister("A") & 0xFF;
            a = ~a & 0xFF;
            registers.setRegister("A", (byte) a);

            setNFlag(registers, true);
            setHFlag(registers, true);
        };
    }

    private OperationExecutor createScfExecutor() {
        return (registers, memory, operands) -> {
            setNFlag(registers, false);
            setHFlag(registers, false);
            setCFlag(registers, true);
        };
    }

    private OperationExecutor createCcfExecutor() {
        return (registers, memory, operands) -> {
            boolean c = (registers.getRegister("F") & 0x10) != 0;
            setNFlag(registers, false);
            setHFlag(registers, false);
            setCFlag(registers, !c);
        };
    }

    private boolean is16BitRegister(String name) {
        return "BC".equals(name) || "DE".equals(name) || "HL".equals(name) || "SP".equals(name) || "AF".equals(name);
    }

    private void set16BitRegister(Registers registers, String name, int value) {
        switch (name) {
            case "BC" -> registers.setBC(value);
            case "DE" -> registers.setDE(value);
            case "HL" -> registers.setHL(value);
            case "SP" -> registers.setSP(value);
            case "AF" -> registers.setAF(value);
        }
    }
}
