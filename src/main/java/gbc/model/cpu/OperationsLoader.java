package gbc.model.cpu;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.memory.Memory;

/**
 * Loads operations, builds flat and grouped opcode maps, and supplies
 * executors for each mnemonic.
 *
 * Fixes applied versus your draft:
 * - Constructor chaining bug: the (Interruptions) constructor now delegates
 * correctly.
 * - Null-safety for interruptions on HALT-bug detection.
 * - Minor warnings clarified; consistent PC/operand consumption for control
 * flow.
 */
public class OperationsLoader {
    private static final Logger LOGGER = Logger.getLogger(OperationsLoader.class.getName());

    private final Map<Integer, Operation> operations;
    private final Map<Integer, Operation> cbOperations;

    // Fast lookup arrays for O(1) access (256 opcodes each)
    private final Operation[] operationsArray = new Operation[256];
    private final Operation[] cbOperationsArray = new Operation[256];

    private final EnumMap<OperationType, Map<Integer, Operation>> groupedOperations;
    private final EnumMap<OperationType, Map<Integer, Operation>> groupedCbOperations;

    private final CPU cpu;
    private final Interruptions interruptions;

    public OperationsLoader() {
        this(null, null);
    }

    public OperationsLoader(Interruptions interruptions) {
        this(interruptions, null); // ✅ previously lost the provided interruptions
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
        registerGroup(unprefixed, operations, groupedOperations, false);
        registerGroup(cbprefixed, cbOperations, groupedCbOperations, true);
        ensureAllOpcodesRegistered(operations, groupedOperations, false);
        ensureAllOpcodesRegistered(cbOperations, groupedCbOperations, true);

        validateOperations(operations, false);
        validateOperations(cbOperations, true);

        // Populate fast lookup arrays
        for (int i = 0; i < 256; i++) {
            operationsArray[i] = operations.get(i);
            cbOperationsArray[i] = cbOperations.get(i);
        }
    }

    private void validateOperations(Map<Integer, Operation> table, boolean cbPrefixed) {
        for (Map.Entry<Integer, Operation> entry : table.entrySet()) {
            int opcode = entry.getKey() & 0xFF;
            Operation op = entry.getValue();
            if (op == null) {
                continue;
            }
            if (op.getMnemonic() == null || op.getMnemonic().isBlank()) {
                op.setMnemonic("INVALID");
                LOGGER.log(Level.WARNING, () -> String.format("Opcode 0x%02X missing mnemonic", opcode));
            }
            if (op.getBytes() <= 0) {
                op.setBytes(cbPrefixed ? 2 : 1);
                LOGGER.log(Level.WARNING, () -> String.format("Opcode 0x%02X missing byte size", opcode));
            }
            if (op.getCycles() == null || op.getCycles().isEmpty()) {
                op.setCycles(java.util.List.of(cbPrefixed ? 8 : 4));
                LOGGER.log(Level.WARNING, () -> String.format("Opcode 0x%02X missing cycle info", opcode));
            }
            if (op.getOperands() == null) {
                op.setOperands(java.util.List.of());
            }
        }
    }

    private void registerGroup(EnumMap<OperationType, Map<Integer, Operation>> source,
            Map<Integer, Operation> flatTarget,
            EnumMap<OperationType, Map<Integer, Operation>> groupedTarget,
            boolean cbPrefixed) {

        for (Map.Entry<OperationType, Map<Integer, Operation>> entry : source.entrySet()) {
            OperationType type = entry.getKey();
            Map<Integer, Operation> bucket = groupedTarget.computeIfAbsent(type, ignored -> new HashMap<>());

            for (Map.Entry<Integer, Operation> opEntry : entry.getValue().entrySet()) {
                int opcode = opEntry.getKey() & 0xFF;
                Operation op = opEntry.getValue();
                OperationExecutor exec = createExecutorForOperation(op);
                if (exec == null) {
                    // Keep opcode registered with INVALID executor to avoid null lookups.
                    Operation invalid = createInvalidOperation(op.isImmediate() ? op.getBytes() : 1, cbPrefixed,
                            op.isImmediate());
                    invalid.setExecutor(createExecutorForOperation(invalid));
                    flatTarget.put(opcode, invalid);
                    bucket.put(opcode, invalid);
                    continue;
                }
                op.setExecutor(exec);
                flatTarget.put(opcode, op);
                bucket.put(opcode, op);
            }
        }
    }

    private void ensureAllOpcodesRegistered(Map<Integer, Operation> flatTarget,
            EnumMap<OperationType, Map<Integer, Operation>> groupedTarget,
            boolean cbPrefixed) {
        Map<Integer, Operation> miscBucket = groupedTarget.computeIfAbsent(OperationType.MISC,
                ignored -> new HashMap<>());
        for (int opcode = 0; opcode <= 0xFF; opcode++) {
            int key = opcode & 0xFF;
            if (!flatTarget.containsKey(key)) {
                Operation invalid = createInvalidOperation(cbPrefixed ? 2 : 1, cbPrefixed, false);
                invalid.setExecutor(createExecutorForOperation(invalid));
                flatTarget.put(key, invalid);
                miscBucket.put(key, invalid);
            }
        }
    }

    private Operation createInvalidOperation(int bytes, boolean cbPrefixed, boolean immediate) {
        Operation op = new Operation();
        op.setMnemonic("INVALID");
        op.setBytes(bytes);
        op.setImmediate(immediate);
        op.setCycles(java.util.List.of(cbPrefixed ? 8 : 4));
        op.setType(OperationType.MISC);
        return op;
    }

    private OperationExecutor createExecutorForOperation(Operation op) {
        String m = op.getMnemonic();

        // Arithmetic/logic first (covers a lot)
        OperationExecutor arith = createArithmeticExecutor(op);
        if (arith != null)
            return arith;

        // Others
        if ("NOP".equals(m))
            return (r, mem, ops) -> {
                /* nop */ };

        if ("RET".equals(m))
            return createRetExecutor();
        if ("RETI".equals(m))
            return createRetiExecutor();

        OperandInfo[] cachedOperands = decodeOperands(op);
        if ("LD".equals(m))
            return createLdExecutor(cachedOperands);
        if ("LDH".equals(m))
            return createLdhExecutor(cachedOperands);

        if ("JP".equals(m))
            return createJpExecutor();
        if ("JR".equals(m))
            return createJrExecutor();
        if ("CALL".equals(m))
            return createCallExecutor();

        if ("SET".equals(m))
            return createSetExecutor();
        if ("RES".equals(m))
            return createResExecutor();
        if ("BIT".equals(m))
            return createBitExecutor();

        if ("PUSH".equals(m))
            return createPushExecutor();
        if ("POP".equals(m))
            return createPopExecutor();

        if ("RLCA".equals(m))
            return createRlcaExecutor();
        if ("RRCA".equals(m))
            return createRrcaExecutor();
        if ("RLA".equals(m))
            return createRlaExecutor();
        if ("RRA".equals(m))
            return createRraExecutor();

        if ("RLC".equals(m))
            return createRlcExecutor();
        if ("RRC".equals(m))
            return createRrcExecutor();
        if ("RL".equals(m))
            return createRlExecutor();
        if ("RR".equals(m))
            return createRrExecutor();
        if ("SLA".equals(m))
            return createSlaExecutor();
        if ("SRA".equals(m))
            return createSraExecutor();
        if ("SWAP".equals(m))
            return createSwapExecutor();
        if ("SRL".equals(m))
            return createSrlExecutor();

        if ("DAA".equals(m))
            return createDaaExecutor();
        if ("CPL".equals(m))
            return createCplExecutor();
        if ("SCF".equals(m))
            return createScfExecutor();
        if ("CCF".equals(m))
            return createCcfExecutor();

        if ("DI".equals(m)) {
            return (regs, mem, ops) -> {
                if (cpu != null) {
                    cpu.setIme(false);
                    cpu.setImePending(false);
                }
            };
        }
        if ("EI".equals(m)) {
            return (regs, mem, ops) -> {
                if (cpu != null) {
                    // enable after next instruction
                    cpu.setImePending(true);
                }
            };
        }
        if ("STOP".equals(m)) {
            return (regs, mem, ops) -> {
                byte second = getImmediateByte(regs, mem);
                if (second != 0x00) {
                    int unexpected = second & 0xFF;
                    // Log at FINE level to reduce verbosity - this is common in some ROMs
                    LOGGER.log(Level.FINE, () -> String.format("STOP second byte unexpected: 0x%02X at PC=0x%04X",
                            unexpected, (regs.getPC() - 2) & 0xFFFF));
                }
                if (cpu != null && cpu.isPrepareSpeedSwitch()) {
                    cpu.setDoubleSpeedMode(!cpu.isDoubleSpeedMode());
                    cpu.setPrepareSpeedSwitch(false);
                    // Timer/DIV side effects
                    mem.writeByte(0xFF04, (byte) 0x00); // DIV reset
                    int tma = mem.readByte(0xFF06) & 0xFF;
                    mem.writeByte(0xFF05, (byte) tma); // TIMA reload
                } else if (cpu != null) {
                    cpu.setStopped(true);
                }
            };
        }
        if ("HALT".equals(m)) {
            return (regs, mem, ops) -> {
                if (cpu != null) {
                    // Halt bug if an interrupt is pending while IME==0.
                    boolean pending = (interruptions != null) && interruptions.hasPendingInterrupt();
                    if (!cpu.isIme() && pending) {
                        cpu.setHaltBugTriggered(true);
                    } else {
                        cpu.setHalted(true);
                    }
                }
            };
        }
        if ("RST".equals(m))
            return createRstExecutor();

        if ("INVALID".equals(m)) {
            final int opBytes = op.getBytes();
            final boolean opImmediate = op.isImmediate();
            return (regs, mem, ops) -> {
                int pc = (regs.getPC() - 1) & 0xFFFF;
                int opcode = mem.readByte(pc) & 0xFF;
                LOGGER.log(Level.WARNING, () -> String.format("Invalid opcode at PC=0x%04X op=0x%02X", pc, opcode));
                if (cpu != null) {
                    cpu.onInvalidOpcode(pc, opcode);
                }
                if (opImmediate && opBytes > 1) {
                    for (int i = 0; i < opBytes - 1; i++) {
                        regs.incrementPC();
                    }
                }
            };
        }

        // Treat unknown mnemonics as invalid to keep behavior consistent.
        return createUnknownExecutor(op);
    }

    private OperationExecutor createUnknownExecutor(Operation op) {
        final int opBytes = Math.max(1, op.getBytes());
        final boolean opImmediate = op.isImmediate();
        return (regs, mem, ops) -> {
            int pc = (regs.getPC() - 1) & 0xFFFF;
            int opcode = mem.readByte(pc) & 0xFF;
            LOGGER.log(Level.WARNING, () -> String.format("Unimplemented opcode at PC=0x%04X op=0x%02X (%s)",
                    pc, opcode, op.getMnemonic()));
            if (cpu != null) {
                cpu.onInvalidOpcode(pc, opcode);
            }
            if (opImmediate && opBytes > 1) {
                for (int i = 0; i < opBytes - 1; i++) {
                    regs.incrementPC();
                }
            }
        };
    }

    public Operation getOperation(int opcode) {
        return operationsArray[opcode & 0xFF];
    }

    public Operation getCbOperation(int opcode) {
        return cbOperationsArray[opcode & 0xFF];
    }

    public Map<Integer, Operation> getOperations(OperationType type) {
        Map<Integer, Operation> bucket = groupedOperations.get(type);
        return bucket == null ? Map.of() : Map.copyOf(bucket);
    }

    public Map<Integer, Operation> getCbOperations(OperationType type) {
        Map<Integer, Operation> bucket = groupedCbOperations.get(type);
        return bucket == null ? Map.of() : Map.copyOf(bucket);
    }

    /* ---------------- Flag helpers ---------------- */

    private void setFlags(Registers r, boolean z, boolean n, boolean h, boolean c) {
        byte f = r.getRegister("F");
        f = (byte) ((f & 0x0F)
                | (z ? 0x80 : 0)
                | (n ? 0x40 : 0)
                | (h ? 0x20 : 0)
                | (c ? 0x10 : 0));
        r.setRegister("F", f);
    }

    private void setZFlag(Registers r, boolean z) {
        r.setRegister("F", (byte) ((r.getRegister("F") & 0x7F) | (z ? 0x80 : 0)));
    }

    private void setNFlag(Registers r, boolean n) {
        r.setRegister("F", (byte) ((r.getRegister("F") & 0xBF) | (n ? 0x40 : 0)));
    }

    private void setHFlag(Registers r, boolean h) {
        r.setRegister("F", (byte) ((r.getRegister("F") & 0xDF) | (h ? 0x20 : 0)));
    }

    private void setCFlag(Registers r, boolean c) {
        r.setRegister("F", (byte) ((r.getRegister("F") & 0xEF) | (c ? 0x10 : 0)));
    }

    private static final class OperandInfo {
        private final String name;
        private final boolean immediate;
        private final boolean memory;

        private OperandInfo(String name, boolean immediate, boolean memory) {
            this.name = name;
            this.immediate = immediate;
            this.memory = memory;
        }
    }

    private OperandInfo[] decodeOperands(Operation op) {
        if (op == null || op.getOperands() == null || op.getOperands().isEmpty()) {
            return new OperandInfo[0];
        }
        int count = op.getOperands().size();
        OperandInfo[] out = new OperandInfo[count];
        for (int i = 0; i < count; i++) {
            Map<String, Object> operand = op.getOperands().get(i);
            if (operand == null) {
                out[i] = null;
                continue;
            }
            String name = (String) operand.get("name");
            boolean immediate = Boolean.TRUE.equals(operand.get("immediate"));
            boolean memory = Boolean.TRUE.equals(operand.get("memory"));
            out[i] = new OperandInfo(name, immediate, memory);
        }
        return out;
    }

    private OperandInfo operandAt(OperandInfo[] operands, int index) {
        if (operands == null || index < 0 || index >= operands.length) {
            return null;
        }
        return operands[index];
    }

    /* ---------------- Immediate helpers ---------------- */

    private byte getImmediateByte(Registers r, Memory m) {
        byte v = (byte) m.readByte(r.getPC());
        r.incrementPC();
        return v;
    }

    private char getImmediateChar(Registers r, Memory m) {
        char v = (char) m.readChar(r.getPC());
        r.incrementPC();
        r.incrementPC();
        return v;
    }

    /* ---------------- Operand R/W helpers ---------------- */

    private int readMemoryOrRegister(Registers r, Memory m, Map<String, Object> operand) {
        String name = (String) operand.get("name");
        Boolean isImmediate = (Boolean) operand.get("immediate");
        Boolean isMemory = (Boolean) operand.get("memory");

        if (Boolean.TRUE.equals(isImmediate)) {
            return switch (name) {
                case "d8", "a8", "r8" -> getImmediateByte(r, m) & 0xFF;
                case "d16", "a16" -> getImmediateChar(r, m) & 0xFFFF;
                default -> 0;
            };
        }

        if (Boolean.TRUE.equals(isMemory)) {
            return switch (name) {
                case "HL" -> m.readByte(r.getHL()) & 0xFF;
                case "HL+" -> {
                    int v = m.readByte(r.getHL()) & 0xFF;
                    r.setHL(r.getHL() + 1);
                    yield v;
                }
                case "HL-" -> {
                    int v = m.readByte(r.getHL()) & 0xFF;
                    r.setHL(r.getHL() - 1);
                    yield v;
                }
                case "BC" -> m.readByte(r.getBC()) & 0xFF;
                case "DE" -> m.readByte(r.getDE()) & 0xFF;
                case "C" -> m.readByte(0xFF00 + (r.getRegister("C") & 0xFF)) & 0xFF;
                default -> 0;
            };
        }

        if (is16BitRegister(name)) {
            return switch (name) {
                case "BC" -> r.getBC() & 0xFFFF;
                case "DE" -> r.getDE() & 0xFFFF;
                case "HL" -> r.getHL() & 0xFFFF;
                case "AF" -> r.getAF() & 0xFFFF;
                case "SP" -> r.getSP() & 0xFFFF;
                default -> 0;
            };
        }

        if ("HL".equals(name) && (isImmediate == null || !isImmediate)) {
            return m.readByte(r.getHL()) & 0xFF;
        }

        if (!is16BitRegister(name) && !"a8".equals(name) && !"a16".equals(name)
                && !"d8".equals(name) && !"d16".equals(name) && !"r8".equals(name)) {
            return r.getRegister(name) & 0xFF;
        }

        LOGGER.log(Level.WARNING, () -> String.format(
                "Unhandled operand in readMemoryOrRegister: %s immediate=%s memory=%s",
                name, isImmediate, isMemory));
        return 0;
    }

    private int readMemoryOrRegister(Registers r, Memory m, OperandInfo operand) {
        if (operand == null) {
            return 0;
        }
        String name = operand.name;
        boolean isImmediate = operand.immediate;
        boolean isMemory = operand.memory;

        if (isImmediate) {
            return switch (name) {
                case "d8", "a8", "r8" -> getImmediateByte(r, m) & 0xFF;
                case "d16", "a16" -> getImmediateChar(r, m) & 0xFFFF;
                default -> 0;
            };
        }

        if (isMemory) {
            return switch (name) {
                case "HL" -> m.readByte(r.getHL()) & 0xFF;
                case "HL+" -> {
                    int v = m.readByte(r.getHL()) & 0xFF;
                    r.setHL(r.getHL() + 1);
                    yield v;
                }
                case "HL-" -> {
                    int v = m.readByte(r.getHL()) & 0xFF;
                    r.setHL(r.getHL() - 1);
                    yield v;
                }
                case "BC" -> m.readByte(r.getBC()) & 0xFF;
                case "DE" -> m.readByte(r.getDE()) & 0xFF;
                case "C" -> m.readByte(0xFF00 + (r.getRegister("C") & 0xFF)) & 0xFF;
                default -> 0;
            };
        }

        if (is16BitRegister(name)) {
            return switch (name) {
                case "BC" -> r.getBC() & 0xFFFF;
                case "DE" -> r.getDE() & 0xFFFF;
                case "HL" -> r.getHL() & 0xFFFF;
                case "AF" -> r.getAF() & 0xFFFF;
                case "SP" -> r.getSP() & 0xFFFF;
                default -> 0;
            };
        }

        if ("HL".equals(name) && !isImmediate) {
            return m.readByte(r.getHL()) & 0xFF;
        }

        if (!is16BitRegister(name) && !"a8".equals(name) && !"a16".equals(name)
                && !"d8".equals(name) && !"d16".equals(name) && !"r8".equals(name)) {
            return r.getRegister(name) & 0xFF;
        }

        LOGGER.log(Level.WARNING, () -> String.format(
                "Unhandled operand in readMemoryOrRegister: %s immediate=%s memory=%s",
                name, isImmediate, isMemory));
        return 0;
    }

    private void writeMemoryOrRegister(Registers r, Memory m, Map<String, Object> operand, int value) {
        String name = (String) operand.get("name");
        Boolean isImmediate = (Boolean) operand.get("immediate");
        Boolean isMemory = (Boolean) operand.get("memory");

        if (Boolean.TRUE.equals(isImmediate))
            return; // read-only

        if (Boolean.TRUE.equals(isMemory)) {
            switch (name) {
                case "HL" -> m.writeByte(r.getHL(), (byte) value);
                case "HL+" -> {
                    m.writeByte(r.getHL(), (byte) value);
                    r.setHL(r.getHL() + 1);
                }
                case "HL-" -> {
                    m.writeByte(r.getHL(), (byte) value);
                    r.setHL(r.getHL() - 1);
                }
                case "BC" -> m.writeByte(r.getBC(), (byte) value);
                case "DE" -> m.writeByte(r.getDE(), (byte) value);
                case "C" -> m.writeByte(0xFF00 + (r.getRegister("C") & 0xFF), (byte) value);
                case "a16" -> {
                    LOGGER.log(Level.WARNING, "a16 memory write should be handled by executor");
                }
                default -> {
                }
            }
            return;
        }

        if ("HL".equals(name) && (isImmediate == null || !isImmediate)) {
            m.writeByte(r.getHL(), (byte) value);
            return;
        }

        if (is16BitRegister(name)) {
            set16BitRegister(r, name, value & 0xFFFF);
            return;
        }

        if (!is16BitRegister(name) && !"a8".equals(name) && !"a16".equals(name)
                && !"d8".equals(name) && !"d16".equals(name) && !"r8".equals(name)) {
            r.setRegister(name, (byte) value);
        } else {
            LOGGER.log(Level.WARNING, () -> String.format(
                    "Unhandled operand in writeMemoryOrRegister: %s immediate=%s memory=%s",
                    name, isImmediate, isMemory));
        }
    }

    private void writeMemoryOrRegister(Registers r, Memory m, OperandInfo operand, int value) {
        if (operand == null) {
            return;
        }
        String name = operand.name;
        boolean isImmediate = operand.immediate;
        boolean isMemory = operand.memory;

        if (isImmediate) {
            return; // read-only
        }

        if (isMemory) {
            switch (name) {
                case "HL" -> m.writeByte(r.getHL(), (byte) value);
                case "HL+" -> {
                    m.writeByte(r.getHL(), (byte) value);
                    r.setHL(r.getHL() + 1);
                }
                case "HL-" -> {
                    m.writeByte(r.getHL(), (byte) value);
                    r.setHL(r.getHL() - 1);
                }
                case "BC" -> m.writeByte(r.getBC(), (byte) value);
                case "DE" -> m.writeByte(r.getDE(), (byte) value);
                case "C" -> m.writeByte(0xFF00 + (r.getRegister("C") & 0xFF), (byte) value);
                case "a16" -> {
                    LOGGER.log(Level.WARNING, "a16 memory write should be handled by executor");
                }
                default -> {
                }
            }
            return;
        }

        if ("HL".equals(name) && !isImmediate) {
            m.writeByte(r.getHL(), (byte) value);
            return;
        }

        if (is16BitRegister(name)) {
            set16BitRegister(r, name, value & 0xFFFF);
            return;
        }

        if (!is16BitRegister(name) && !"a8".equals(name) && !"a16".equals(name)
                && !"d8".equals(name) && !"d16".equals(name) && !"r8".equals(name)) {
            r.setRegister(name, (byte) value);
        } else {
            LOGGER.log(Level.WARNING, () -> String.format(
                    "Unhandled operand in writeMemoryOrRegister: %s immediate=%s memory=%s",
                    name, isImmediate, isMemory));
        }
    }

    /* ---------------- Arithmetic/Logic factories ---------------- */

    private OperationExecutor createArithmeticExecutor(Operation op) {
        String m = op.getMnemonic();
        if ("ADD".equals(m))
            return createAddExecutor(decodeOperands(op));
        if ("ADC".equals(m))
            return createAdcExecutor(decodeOperands(op));
        if ("SUB".equals(m))
            return createSubExecutor(decodeOperands(op));
        if ("SBC".equals(m))
            return createSbcExecutor(decodeOperands(op));
        if ("AND".equals(m))
            return createAndExecutor(decodeOperands(op));
        if ("OR".equals(m))
            return createOrExecutor(decodeOperands(op));
        if ("XOR".equals(m))
            return createXorExecutor(decodeOperands(op));
        if ("CP".equals(m))
            return createCpExecutor(decodeOperands(op));
        if ("INC".equals(m))
            return createIncExecutor(decodeOperands(op));
        if ("DEC".equals(m))
            return createDecExecutor(decodeOperands(op));
        return null;
    }

    private OperationExecutor createAddExecutor(OperandInfo[] operands) {
        final OperandInfo dst = operandAt(operands, 0);
        final OperandInfo src = operandAt(operands, 1);
        final String d = dst != null ? dst.name : null;
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (dst == null || src == null)
                return;

            // ADD HL, rr
            if ("HL".equals(d) && is16BitRegister(s) && !dst.immediate) {
                int hl = r.getHL() & 0xFFFF;
                int rhs = switch (s) {
                    case "BC" -> r.getBC() & 0xFFFF;
                    case "DE" -> r.getDE() & 0xFFFF;
                    case "HL" -> r.getHL() & 0xFFFF;
                    case "SP" -> r.getSP() & 0xFFFF;
                    default -> 0;
                };
                int sum = hl + rhs;
                r.setHL((char) (sum & 0xFFFF));
                setHFlag(r, ((hl & 0x0FFF) + (rhs & 0x0FFF)) > 0x0FFF);
                setCFlag(r, sum > 0xFFFF);
                setNFlag(r, false); // N=0; Z unaffected per spec
                return;
            }

            // ADD SP, r8
            if ("SP".equals(d) && "r8".equals(s)) {
                byte off = getImmediateByte(r, m); // M2: read offset
                // M3-M4: two internal M-cycles for 16-bit addition
                if (cpu != null) {
                    cpu.tickInternalMcycle();
                    cpu.tickInternalMcycle();
                }
                int sp = r.getSP() & 0xFFFF;
                int soff = (byte) off; // sign-extend
                int res = (sp + soff) & 0xFFFF;
                setFlags(r, false, false,
                        ((sp & 0x0F) + (soff & 0x0F)) > 0x0F,
                        ((sp & 0xFF) + (soff & 0xFF)) > 0xFF);
                r.setSP((char) res);
                return;
            }

            // 8-bit ADD r, x
            byte dest = r.getRegister(d);
            byte srcb = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            int result = (dest & 0xFF) + (srcb & 0xFF);
            byte out = (byte) (result & 0xFF);
            r.setRegister(d, out);
            setFlags(r, out == 0, false,
                    ((dest & 0x0F) + (srcb & 0x0F)) > 0x0F,
                    result > 0xFF);
        };
    }

    private OperationExecutor createAdcExecutor(OperandInfo[] operands) {
        final OperandInfo dst = operandAt(operands, 0);
        final OperandInfo src = operandAt(operands, 1);
        final String d = dst != null ? dst.name : null;
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (dst == null || src == null)
                return;

            byte a = r.getRegister(d);
            byte carry = (byte) ((r.getRegister("F") & 0x10) >> 4);
            byte b = "d8".equals(s)
                    ? getImmediateByte(r, m)
                    : (byte) readMemoryOrRegister(r, m, src);
            int res = (a & 0xFF) + (b & 0xFF) + carry;
            byte out = (byte) (res & 0xFF);
            r.setRegister(d, out);
            setFlags(r, out == 0, false,
                    ((a & 0x0F) + (b & 0x0F) + carry) > 0x0F,
                    res > 0xFF);
        };
    }

    private OperationExecutor createSubExecutor(OperandInfo[] operands) {
        final OperandInfo src = operandAt(operands, operands.length - 1);
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (src == null)
                return;
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            int res = (a & 0xFF) - (b & 0xFF);
            byte out = (byte) (res & 0xFF);
            r.setRegister("A", out);
            setFlags(r, out == 0, true,
                    ((a & 0x0F) - (b & 0x0F)) < 0,
                    res < 0);
        };
    }

    private OperationExecutor createSbcExecutor(OperandInfo[] operands) {
        final OperandInfo src = operandAt(operands, operands.length - 1);
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (src == null)
                return;
            byte a = r.getRegister("A");
            byte carry = (byte) ((r.getRegister("F") & 0x10) >> 4);
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            int res = (a & 0xFF) - (b & 0xFF) - carry;
            byte out = (byte) (res & 0xFF);
            r.setRegister("A", out);
            setFlags(r, out == 0, true,
                    ((a & 0x0F) - (b & 0x0F) - carry) < 0,
                    res < 0);
        };
    }

    private OperationExecutor createAndExecutor(OperandInfo[] operands) {
        final OperandInfo src = operandAt(operands, operands.length - 1);
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (src == null)
                return;
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) (readMemoryOrRegister(r, m, src) & 0xFF);
            byte out = (byte) (a & b);
            r.setRegister("A", out);
            setFlags(r, out == 0, false, true, false);
        };
    }

    private OperationExecutor createOrExecutor(OperandInfo[] operands) {
        final OperandInfo src = operandAt(operands, operands.length - 1);
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (src == null)
                return;
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            byte out = (byte) (a | b);
            r.setRegister("A", out);
            setFlags(r, out == 0, false, false, false);
        };
    }

    private OperationExecutor createXorExecutor(OperandInfo[] operands) {
        final OperandInfo src = operandAt(operands, operands.length - 1);
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (src == null)
                return;
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            byte out = (byte) (a ^ b);
            r.setRegister("A", out);
            setFlags(r, out == 0, false, false, false);
        };
    }

    private OperationExecutor createCpExecutor(OperandInfo[] operands) {
        final OperandInfo src = operandAt(operands, operands.length - 1);
        final String s = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (src == null)
                return;
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            int res = (a & 0xFF) - (b & 0xFF);
            byte out = (byte) (res & 0xFF);
            setFlags(r, out == 0, true, ((a & 0x0F) - (b & 0x0F)) < 0, res < 0);
        };
    }

    private OperationExecutor createIncExecutor(OperandInfo[] operands) {
        final OperandInfo dst = operandAt(operands, 0);
        final String d = dst != null ? dst.name : null;
        return (r, m, ops) -> {
            if (dst == null)
                return;

            // INC (HL)
            if (dst.memory && "HL".equals(d)) {
                byte v = (byte) m.readByte(r.getHL());
                int res = (v & 0xFF) + 1;
                byte out = (byte) (res & 0xFF);
                m.writeByte(r.getHL(), out);
                setZFlag(r, out == 0);
                setNFlag(r, false);
                setHFlag(r, ((v & 0x0F) + 1) > 0x0F);
                return;
            }

            // INC 16-bit (flags unaffected)
            if (is16BitRegister(d)) {
                int val = switch (d) {
                    case "BC" -> (r.getBC() + 1) & 0xFFFF;
                    case "DE" -> (r.getDE() + 1) & 0xFFFF;
                    case "HL" -> (r.getHL() + 1) & 0xFFFF;
                    case "SP" -> (r.getSP() + 1) & 0xFFFF;
                    default -> 0;
                };
                set16BitRegister(r, d, val);
                return;
            }

            // INC r
            byte v = r.getRegister(d);
            int res = (v & 0xFF) + 1;
            byte out = (byte) (res & 0xFF);
            r.setRegister(d, out);
            setZFlag(r, out == 0);
            setNFlag(r, false);
            setHFlag(r, ((v & 0x0F) + 1) > 0x0F);
        };
    }

    private OperationExecutor createDecExecutor(OperandInfo[] operands) {
        final OperandInfo dst = operandAt(operands, 0);
        final String d = dst != null ? dst.name : null;
        return (r, m, ops) -> {
            if (dst == null)
                return;

            // DEC (HL)
            if (dst.memory && "HL".equals(d)) {
                byte v = (byte) m.readByte(r.getHL());
                int res = (v & 0xFF) - 1;
                byte out = (byte) (res & 0xFF);
                m.writeByte(r.getHL(), out);
                setZFlag(r, out == 0);
                setNFlag(r, true);
                setHFlag(r, ((v & 0x0F) - 1) < 0);
                return;
            }

            // DEC 16-bit (flags unaffected)
            if (is16BitRegister(d)) {
                int val = switch (d) {
                    case "BC" -> (r.getBC() - 1) & 0xFFFF;
                    case "DE" -> (r.getDE() - 1) & 0xFFFF;
                    case "HL" -> (r.getHL() - 1) & 0xFFFF;
                    case "SP" -> (r.getSP() - 1) & 0xFFFF;
                    default -> 0;
                };
                set16BitRegister(r, d, val);
                return;
            }

            // DEC r
            byte v = r.getRegister(d);
            int res = (v & 0xFF) - 1;
            byte out = (byte) (res & 0xFF);
            r.setRegister(d, out);
            setZFlag(r, out == 0);
            setNFlag(r, true);
            setHFlag(r, ((v & 0x0F) - 1) < 0);
        };
    }

    /* ---------------- LD / LDH ---------------- */

    private OperationExecutor createLdExecutor(OperandInfo[] operands) {
        final OperandInfo dest = operandAt(operands, 0);
        final OperandInfo src = operandAt(operands, 1);
        final String dn = dest != null ? dest.name : null;
        final String sn = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (dest == null || src == null)
                return;

            // (a16),SP
            if ("a16".equals(dn) && "SP".equals(sn)) {
                int addr = getImmediateChar(r, m);
                m.writeChar(addr, r.getSP());
                return;
            }
            // (a16),A
            if ("a16".equals(dn) && "A".equals(sn)) {
                int addr = getImmediateChar(r, m);
                m.writeByte(addr, r.getRegister("A"));
                return;
            }
            // A,(a16)
            if ("A".equals(dn) && "a16".equals(sn)) {
                int addr = getImmediateChar(r, m);
                r.setRegister("A", (byte) m.readByte(addr));
                return;
            }
            // HL = SP + r8
            if ("HL".equals(dn) && "SP+r8".equals(sn)) {
                byte off = getImmediateByte(r, m); // M2: read offset
                // M3: internal M-cycle for 16-bit addition
                if (cpu != null)
                    cpu.tickInternalMcycle();
                int sp = r.getSP() & 0xFFFF;
                int so = (byte) off;
                int res = (sp + so) & 0xFFFF;
                setFlags(r, false, false,
                        ((sp & 0x0F) + (so & 0x0F)) > 0x0F,
                        ((sp & 0xFF) + (so & 0xFF)) > 0xFF);
                r.setHL((char) res);
                return;
            }
            // rr = d16
            if (is16BitRegister(dn) && "d16".equals(sn)) {
                int v = getImmediateChar(r, m);
                set16BitRegister(r, dn, v);
                return;
            }

            // Generic 8-bit / (HL) transfers
            int value = readMemoryOrRegister(r, m, src);
            writeMemoryOrRegister(r, m, dest, value);
        };
    }

    private OperationExecutor createLdhExecutor(OperandInfo[] operands) {
        final OperandInfo dest = operandAt(operands, 0);
        final OperandInfo src = operandAt(operands, 1);
        final String dn = dest != null ? dest.name : null;
        final String sn = src != null ? src.name : null;
        return (r, m, ops) -> {
            if (dest == null || src == null)
                return;

            if ("A".equals(dn) && "a8".equals(sn)) {
                int addr = 0xFF00 + (getImmediateByte(r, m) & 0xFF);
                r.setRegister("A", (byte) m.readByte(addr));
            } else if ("a8".equals(dn) && "A".equals(sn)) {
                int addr = 0xFF00 + (getImmediateByte(r, m) & 0xFF);
                m.writeByte(addr, r.getRegister("A"));
            } else if ("A".equals(dn) && "C".equals(sn)) {
                int addr = 0xFF00 + (r.getRegister("C") & 0xFF);
                r.setRegister("A", (byte) m.readByte(addr));
            } else if ("C".equals(dn) && "A".equals(sn)) {
                int addr = 0xFF00 + (r.getRegister("C") & 0xFF);
                m.writeByte(addr, r.getRegister("A"));
            }
        };
    }

    /* ---------------- Control flow ---------------- */

    private OperationExecutor createJpExecutor() {
        return (r, m, ops) -> {
            if (ops.isEmpty())
                return;
            boolean cond = true;
            boolean isConditional = ops.size() == 2;
            if (isConditional) {
                cond = checkCondition(r, (String) ops.get(0).get("name"));
            }
            Map<String, Object> target = ops.get(ops.size() - 1);
            String targetName = (String) target.get("name");
            int addr = readMemoryOrRegister(r, m, target);
            if (cond) {
                // Internal M-cycle after reading target address (only for JP a16, not JP HL)
                if (cpu != null && !"HL".equals(targetName))
                    cpu.tickInternalMcycle();
                r.setPC(addr);
            }
            if (cpu != null)
                cpu.setLastConditionTaken(cond && isConditional);
        };
    }

    private OperationExecutor createJrExecutor() {
        return (r, m, ops) -> {
            if (ops.isEmpty())
                return;
            boolean cond = true;
            if (ops.size() == 2) {
                cond = checkCondition(r, (String) ops.get(0).get("name"));
            }
            int off = getImmediateByte(r, m); // M2: read offset
            if (cond) {
                // M3: internal M-cycle for PC adjustment
                if (cpu != null)
                    cpu.tickInternalMcycle();
                r.setPC(r.getPC() + (byte) off);
            }
            if (cpu != null)
                cpu.setLastConditionTaken(cond && ops.size() == 2);
        };
    }

    private OperationExecutor createCallExecutor() {
        return (r, m, ops) -> {
            if (ops.isEmpty())
                return;
            boolean cond = true;
            int idx = 0;
            if (ops.size() == 2) {
                cond = checkCondition(r, (String) ops.get(0).get("name"));
                idx = 1;
            }
            int addr = readMemoryOrRegister(r, m, ops.get(idx)); // reads a16 (2 M-cycles)
            if (cond) {
                // M4: internal cycle between operand read and stack push
                if (cpu != null)
                    cpu.tickInternalMcycle();
                int ret = r.getPC();
                r.setSP(r.getSP() - 2);
                // Stack push: high byte first, then low byte (hardware order)
                m.writeByte(r.getSP() + 1, (ret >> 8) & 0xFF); // M5: push high
                m.writeByte(r.getSP(), ret & 0xFF); // M6: push low
                r.setPC(addr);
            }
            if (cpu != null)
                cpu.setLastConditionTaken(cond && ops.size() == 2);
        };
    }

    private OperationExecutor createRetExecutor() {
        return (r, m, ops) -> {
            boolean cond = true;
            boolean isConditional = ops.size() == 1;
            if (isConditional) {
                cond = checkCondition(r, (String) ops.get(0).get("name"));
                // RET cc: internal M-cycle after condition check (before stack read)
                if (cpu != null)
                    cpu.tickInternalMcycle();
            }
            if (cond) {
                int ret = m.readChar(r.getSP()); // M2-M3 (or M3-M4 for cc): read return address
                r.setSP(r.getSP() + 2);
                r.setPC(ret);
                // Internal M-cycle after stack read (set new PC)
                if (cpu != null)
                    cpu.tickInternalMcycle();
            }
            if (cpu != null)
                cpu.setLastConditionTaken(cond && isConditional);
        };
    }

    private OperationExecutor createRetiExecutor() {
        return (r, m, ops) -> {
            int ret = m.readChar(r.getSP()); // M2-M3: read return address
            r.setSP(r.getSP() + 2);
            r.setPC(ret);
            // M4: internal M-cycle after stack read
            if (cpu != null) {
                cpu.tickInternalMcycle();
                cpu.setIme(true); // RETI enables immediately
            }
        };
    }

    private OperationExecutor createRstExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            String s = (String) ops.get(0).get("name"); // e.g., "08H"
            int addr = Integer.parseInt(s.substring(0, s.length() - 1), 16);
            int ret = r.getPC();
            // M2: internal M-cycle before stack push
            if (cpu != null)
                cpu.tickInternalMcycle();
            r.setSP(r.getSP() - 2);
            // Stack push: high byte first, then low byte (hardware order)
            m.writeByte(r.getSP() + 1, (ret >> 8) & 0xFF); // M3: push high
            m.writeByte(r.getSP(), ret & 0xFF); // M4: push low
            r.setPC(addr);
        };
    }

    private boolean checkCondition(Registers r, String name) {
        byte f = r.getRegister("F");
        return switch (name) {
            case "NZ" -> (f & 0x80) == 0;
            case "Z" -> (f & 0x80) != 0;
            case "NC" -> (f & 0x10) == 0;
            case "C" -> (f & 0x10) != 0;
            default -> true;
        };
    }

    /* ---------------- Bit/rotate/shift ---------------- */

    private OperationExecutor createSetExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 2)
                return;
            int bit = Integer.parseInt((String) ops.get(0).get("name"));
            Map<String, Object> tgt = ops.get(1);
            int v = readMemoryOrRegister(r, m, tgt);
            v |= (1 << bit);
            writeMemoryOrRegister(r, m, tgt, v);
        };
    }

    private OperationExecutor createResExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 2)
                return;
            int bit = Integer.parseInt((String) ops.get(0).get("name"));
            Map<String, Object> tgt = ops.get(1);
            int v = readMemoryOrRegister(r, m, tgt);
            v &= ~(1 << bit);
            writeMemoryOrRegister(r, m, tgt, v);
        };
    }

    private OperationExecutor createBitExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 2)
                return;
            int bit = Integer.parseInt((String) ops.get(0).get("name"));
            Map<String, Object> tgt = ops.get(1);
            int v = readMemoryOrRegister(r, m, tgt);
            boolean set = (v & (1 << bit)) != 0;
            setZFlag(r, !set);
            setNFlag(r, false);
            setHFlag(r, true);
            // C unchanged
        };
    }

    private OperationExecutor createRlcaExecutor() {
        return (r, m, ops) -> {
            int v = r.getRegister("A") & 0xFF;
            int c = (v & 0x80) >> 7;
            v = ((v << 1) | c) & 0xFF;
            r.setRegister("A", (byte) v);
            setZFlag(r, false);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createRrcaExecutor() {
        return (r, m, ops) -> {
            int v = r.getRegister("A") & 0xFF;
            int c = v & 0x01;
            v = ((v >> 1) | (c << 7)) & 0xFF;
            r.setRegister("A", (byte) v);
            setZFlag(r, false);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createRlaExecutor() {
        return (r, m, ops) -> {
            int v = r.getRegister("A") & 0xFF;
            int cin = (r.getRegister("F") & 0x10) >> 4;
            int cout = (v & 0x80) >> 7;
            v = ((v << 1) | cin) & 0xFF;
            r.setRegister("A", (byte) v);
            setZFlag(r, false);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, cout != 0);
        };
    }

    private OperationExecutor createRraExecutor() {
        return (r, m, ops) -> {
            int v = r.getRegister("A") & 0xFF;
            int cin = (r.getRegister("F") & 0x10) >> 4;
            int cout = v & 0x01;
            v = ((v >> 1) | (cin << 7)) & 0xFF;
            r.setRegister("A", (byte) v);
            setZFlag(r, false);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, cout != 0);
        };
    }

    private OperationExecutor createRlcExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int c = (v & 0x80) >> 7;
            v = ((v << 1) | c) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createRrcExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int c = v & 0x01;
            v = ((v >> 1) | (c << 7)) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createRlExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int cin = (r.getRegister("F") & 0x10) >> 4;
            int cout = (v & 0x80) >> 7;
            v = ((v << 1) | cin) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, cout != 0);
        };
    }

    private OperationExecutor createRrExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int cin = (r.getRegister("F") & 0x10) >> 4;
            int cout = v & 0x01;
            v = ((v >> 1) | (cin << 7)) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, cout != 0);
        };
    }

    private OperationExecutor createSlaExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int c = (v & 0x80) >> 7;
            v = (v << 1) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createSraExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int c = v & 0x01;
            int msb = v & 0x80;
            v = ((v >> 1) | msb) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createSrlExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            int c = v & 0x01;
            v = (v >> 1) & 0xFF;
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, c != 0);
        };
    }

    private OperationExecutor createSwapExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> t = ops.get(0);
            int v = readMemoryOrRegister(r, m, t);
            v = ((v & 0x0F) << 4) | ((v & 0xF0) >> 4);
            writeMemoryOrRegister(r, m, t, v);
            setZFlag(r, (v & 0xFF) == 0);
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, false);
        };
    }

    /* ---------------- Misc ---------------- */

    private OperationExecutor createPushExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> src = ops.get(0);
            String name = (String) src.get("name");

            int value = switch (name) {
                case "BC" -> r.getBC() & 0xFFFF;
                case "DE" -> r.getDE() & 0xFFFF;
                case "HL" -> r.getHL() & 0xFFFF;
                case "AF" -> r.getAF() & 0xFFFF;
                default -> 0;
            };

            // M2: internal M-cycle before stack write
            if (cpu != null)
                cpu.tickInternalMcycle();
            r.setSP(r.getSP() - 2);
            // Stack push: high byte first, then low byte (hardware order)
            m.writeByte(r.getSP() + 1, (value >> 8) & 0xFF); // M3: push high
            m.writeByte(r.getSP(), value & 0xFF); // M4: push low
        };
    }

    private OperationExecutor createPopExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            Map<String, Object> dest = ops.get(0);
            String name = (String) dest.get("name");

            int value = m.readChar(r.getSP()) & 0xFFFF;
            r.setSP(r.getSP() + 2);

            switch (name) {
                case "BC" -> r.setBC(value);
                case "DE" -> r.setDE(value);
                case "HL" -> r.setHL(value);
                case "AF" -> {
                    // For AF, the lower 4 bits of F are always 0
                    value &= 0xFFF0;
                    r.setAF(value);
                }
            }
        };
    }

    private OperationExecutor createDaaExecutor() {
        return (r, m, ops) -> {
            int a = r.getRegister("A") & 0xFF;
            byte f = r.getRegister("F");
            boolean n = (f & 0x40) != 0;
            boolean h = (f & 0x20) != 0;
            boolean c = (f & 0x10) != 0;

            if (!n) {
                if (c || a > 0x99) {
                    a = (a + 0x60) & 0xFF;
                    c = true;
                }
                if (h || (a & 0x0F) > 0x09) {
                    a = (a + 0x06) & 0xFF;
                }
            } else {
                if (c)
                    a = (a - 0x60) & 0xFF;
                if (h)
                    a = (a - 0x06) & 0xFF;
            }

            r.setRegister("A", (byte) a);
            setZFlag(r, a == 0);
            setHFlag(r, false);
            setCFlag(r, c);
        };
    }

    private OperationExecutor createCplExecutor() {
        return (r, m, ops) -> {
            int a = r.getRegister("A") & 0xFF;
            a = (~a) & 0xFF;
            r.setRegister("A", (byte) a);
            setNFlag(r, true);
            setHFlag(r, true);
        };
    }

    private OperationExecutor createScfExecutor() {
        return (r, m, ops) -> {
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, true);
        };
    }

    private OperationExecutor createCcfExecutor() {
        return (r, m, ops) -> {
            boolean c = (r.getRegister("F") & 0x10) != 0;
            setNFlag(r, false);
            setHFlag(r, false);
            setCFlag(r, !c);
        };
    }

    /* ---------------- 16-bit helpers ---------------- */

    private boolean is16BitRegister(String name) {
        return "BC".equals(name) || "DE".equals(name) || "HL".equals(name) || "SP".equals(name) || "AF".equals(name);
    }

    private void set16BitRegister(Registers r, String name, int value) {
        switch (name) {
            case "BC" -> r.setBC(value);
            case "DE" -> r.setDE(value);
            case "HL" -> r.setHL(value);
            case "SP" -> r.setSP(value);
            case "AF" -> r.setAF(value);
            default -> {
            }
        }
    }
}
