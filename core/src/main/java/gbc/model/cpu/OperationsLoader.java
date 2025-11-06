package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

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
                Operation op = opEntry.getValue();
                OperationExecutor exec = createExecutorForOperation(op);
                if (exec == null) {
                    // Leave opcode unregistered to avoid executing without behaviour
                    continue;
                }
                op.setExecutor(exec);
                int opcode = opEntry.getKey() & 0xFF;
                flatTarget.put(opcode, op);
                bucket.put(opcode, op);
            }
        }
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

        if ("LD".equals(m))
            return createLdExecutor();
        if ("LDH".equals(m))
            return createLdhExecutor();

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
                    System.err.printf("WARNING: STOP second byte unexpected: 0x%02X%n", second & 0xFF);
                }
                if (cpu != null && cpu.isPrepareSpeedSwitch()) {
                    cpu.setDoubleSpeedMode(!cpu.isDoubleSpeedMode());
                    cpu.setPrepareSpeedSwitch(false);
                    // Timer/DIV side effects
                    mem.writeByte(0xFF04, (byte) 0x00); // DIV reset
                    int tma = mem.readByte(0xFF06) & 0xFF;
                    mem.writeByte(0xFF05, (byte) tma); // TIMA reload
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
            return (regs, mem, ops) -> {
                System.err.printf("WARNING: Invalid opcode at PC=0x%04X op=0x%02X%n",
                        (int) regs.getPC(), (int) mem.readByte(regs.getPC()) & 0xFF);
                // Advance 1 to avoid lock
                regs.incrementPC();
            };
        }

        // Fallback for unimplemented mnemonics: consume bytes so execution can continue
        return (regs, mem, ops) -> {
            System.err.printf("WARNING: Unimplemented operation %s at PC=0x%04X%n",
                    op.getMnemonic(), (int) regs.getPC());
            int b = op.getBytes();
            if (b > 1) {
                for (int i = 0; i < b - 1; i++)
                    regs.incrementPC();
            } else {
                regs.incrementPC();
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

    /* ---------------- Immediate helpers ---------------- */

    private byte getImmediateByte(Registers r, Memory m) {
        if (cpu != null) {
            cpu.step(4);
        }
        byte v = (byte) m.readByte(r.getPC());
        r.incrementPC();
        return v;
    }

    private char getImmediateChar(Registers r, Memory m) {
        if (cpu != null) {
            cpu.step(8);
        }
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

        System.err.println("Unhandled operand in readMemoryOrRegister: " + name + " immediate=" + isImmediate
                + " memory=" + isMemory);
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
                case "a16" -> System.err.println("Warning: a16 memory write should be handled by executor");
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
            System.err.println("Unhandled operand in writeMemoryOrRegister: " + name + " immediate=" + isImmediate
                    + " memory=" + isMemory);
        }
    }

    /* ---------------- Arithmetic/Logic factories ---------------- */

    private OperationExecutor createArithmeticExecutor(Operation op) {
        String m = op.getMnemonic();
        if ("ADD".equals(m))
            return createAddExecutor();
        if ("ADC".equals(m))
            return createAdcExecutor();
        if ("SUB".equals(m))
            return createSubExecutor();
        if ("SBC".equals(m))
            return createSbcExecutor();
        if ("AND".equals(m))
            return createAndExecutor();
        if ("OR".equals(m))
            return createOrExecutor();
        if ("XOR".equals(m))
            return createXorExecutor();
        if ("CP".equals(m))
            return createCpExecutor();
        if ("INC".equals(m))
            return createIncExecutor();
        if ("DEC".equals(m))
            return createDecExecutor();
        return null;
    }

    private OperationExecutor createAddExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 2)
                return;
            Map<String, Object> dst = ops.get(0);
            Map<String, Object> src = ops.get(1);
            String d = (String) dst.get("name");
            String s = (String) src.get("name");

            // ADD HL, rr
            if ("HL".equals(d) && is16BitRegister(s) && !Boolean.TRUE.equals(dst.get("immediate"))) {
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
                byte off = getImmediateByte(r, m);
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

    private OperationExecutor createAdcExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 2)
                return;
            Map<String, Object> dst = ops.get(0);
            Map<String, Object> src = ops.get(1);
            String d = (String) dst.get("name");
            String s = (String) src.get("name");

            byte a = r.getRegister(d);
            byte carry = (byte) ((r.getRegister("F") & 0x10) >> 4);
            byte b = "d8".equals(s)
                    ? getImmediateByte(r, m)
                    : (byte) (("HL".equals(s)) ? m.readByte(r.getHL()) : readMemoryOrRegister(r, m, src));
            int res = (a & 0xFF) + (b & 0xFF) + carry;
            byte out = (byte) (res & 0xFF);
            r.setRegister(d, out);
            setFlags(r, out == 0, false,
                    ((a & 0x0F) + (b & 0x0F) + carry) > 0x0F,
                    res > 0xFF);
        };
    }

    private OperationExecutor createSubExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> src = ops.get(0);
            String s = (String) src.get("name");
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

    private OperationExecutor createSbcExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> src = ops.get(0);
            String s = (String) src.get("name");
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

    private OperationExecutor createAndExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> src = ops.get(0);
            String s = (String) src.get("name");
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) (readMemoryOrRegister(r, m, src) & 0xFF);
            byte out = (byte) (a & b);
            r.setRegister("A", out);
            setFlags(r, out == 0, false, true, false);
        };
    }

    private OperationExecutor createOrExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> src = ops.get(0);
            String s = (String) src.get("name");
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            byte out = (byte) (a | b);
            r.setRegister("A", out);
            setFlags(r, out == 0, false, false, false);
        };
    }

    private OperationExecutor createXorExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> src = ops.get(0);
            String s = (String) src.get("name");
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            byte out = (byte) (a ^ b);
            r.setRegister("A", out);
            setFlags(r, out == 0, false, false, false);
        };
    }

    private OperationExecutor createCpExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> src = ops.get(0);
            String s = (String) src.get("name");
            byte a = r.getRegister("A");
            byte b = "d8".equals(s) ? getImmediateByte(r, m) : (byte) readMemoryOrRegister(r, m, src);
            int res = (a & 0xFF) - (b & 0xFF);
            byte out = (byte) (res & 0xFF);
            setFlags(r, out == 0, true, ((a & 0x0F) - (b & 0x0F)) < 0, res < 0);
        };
    }

    private OperationExecutor createIncExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> dst = ops.get(0);
            String d = (String) dst.get("name");
            Boolean mem = (Boolean) dst.get("memory");
            Boolean imm = (Boolean) dst.get("immediate");

            // INC (HL)
            if ((Boolean.TRUE.equals(mem) && "HL".equals(d))
                    || (mem == null && "HL".equals(d) && imm != null && !imm)) {
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

    private OperationExecutor createDecExecutor() {
        return (r, m, ops) -> {
            if (ops == null || ops.size() < 1)
                return;
            Map<String, Object> dst = ops.get(0);
            String d = (String) dst.get("name");
            Boolean mem = (Boolean) dst.get("memory");
            Boolean imm = (Boolean) dst.get("immediate");

            // DEC (HL)
            if ((Boolean.TRUE.equals(mem) && "HL".equals(d))
                    || (mem == null && "HL".equals(d) && imm != null && !imm)) {
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

    private OperationExecutor createLdExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 2)
                return;
            Map<String, Object> dest = ops.get(0);
            Map<String, Object> src = ops.get(1);
            String dn = (String) dest.get("name");
            String sn = (String) src.get("name");

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
                byte off = getImmediateByte(r, m);
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

    private OperationExecutor createLdhExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 2)
                return;
            Map<String, Object> dest = ops.get(0);
            Map<String, Object> src = ops.get(1);
            String dn = (String) dest.get("name");
            String sn = (String) src.get("name");

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
            if (ops.size() == 2) {
                cond = checkCondition(r, (String) ops.get(0).get("name"));
            }
            int addr = readMemoryOrRegister(r, m, ops.get(ops.size() - 1));
            if (cond)
                r.setPC(addr);
            if (cpu != null)
                cpu.setLastConditionTaken(cond && ops.size() == 2);
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
            int off = getImmediateByte(r, m);
            if (cond)
                r.setPC(r.getPC() + (byte) off);
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
            int addr = readMemoryOrRegister(r, m, ops.get(idx)); // advances PC
            if (cond) {
                int ret = r.getPC();
                r.setSP(r.getSP() - 2);
                m.writeChar(r.getSP(), ret);
                r.setPC(addr);
            }
            if (cpu != null)
                cpu.setLastConditionTaken(cond && ops.size() == 2);
        };
    }

    private OperationExecutor createRetExecutor() {
        return (r, m, ops) -> {
            boolean cond = true;
            if (ops.size() == 1) {
                cond = checkCondition(r, (String) ops.get(0).get("name"));
            }
            if (cond) {
                int ret = m.readChar(r.getSP());
                r.setSP(r.getSP() + 2);
                r.setPC(ret);
            }
            if (cpu != null)
                cpu.setLastConditionTaken(cond && ops.size() == 1);
        };
    }

    private OperationExecutor createRetiExecutor() {
        return (r, m, ops) -> {
            int ret = m.readChar(r.getSP());
            r.setSP(r.getSP() + 2);
            r.setPC(ret);
            if (cpu != null)
                cpu.setIme(true); // RETI enables immediately
        };
    }

    private OperationExecutor createRstExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1)
                return;
            String s = (String) ops.get(0).get("name"); // e.g., "08H"
            int addr = Integer.parseInt(s.substring(0, s.length() - 1), 16);
            int ret = r.getPC();
            r.setSP(r.getSP() - 2);
            m.writeChar(r.getSP(), ret);
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
            if (ops.size() != 1) return;
            Map<String, Object> src = ops.get(0);
            String name = (String) src.get("name");

            int value = switch (name) {
                case "BC" -> r.getBC() & 0xFFFF;
                case "DE" -> r.getDE() & 0xFFFF;
                case "HL" -> r.getHL() & 0xFFFF;
                case "AF" -> r.getAF() & 0xFFFF;
                default -> 0;
            };

            r.setSP(r.getSP() - 2);
            m.writeChar(r.getSP(), value);
        };
    }

    private OperationExecutor createPopExecutor() {
        return (r, m, ops) -> {
            if (ops.size() != 1) return;
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
