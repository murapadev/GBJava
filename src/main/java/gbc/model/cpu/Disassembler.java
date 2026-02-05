package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Static helper that converts raw opcodes into human-friendly mnemonics using
 * the existing {@link OperationDefinitions}. The implementation is
 * intentionally
 * read-only so it can be reused by the debugger UI without needing an active
 * {@link CPU} instance.
 */
public final class Disassembler {
    private static final Map<Integer, Operation> UNPREFIXED;
    private static final Map<Integer, Operation> CB_PREFIXED;
    private static final Set<String> CONDITIONS = Set.of("NZ", "Z", "NC", "C");

    static {
        UNPREFIXED = Collections.unmodifiableMap(flatten(OperationDefinitions.buildUnprefixedTable()));
        CB_PREFIXED = Collections.unmodifiableMap(flatten(OperationDefinitions.buildCbPrefixedTable()));
    }

    private Disassembler() {
    }

    public static DecodedInstruction decode(Memory memory, int address) {
        Objects.requireNonNull(memory, "memory");
        int pc = address & 0xFFFF;
        int opcode = memory.peekByte(pc) & 0xFF;

        boolean cbPrefixed = opcode == 0xCB;
        Operation operation;
        int instructionBytes;
        if (cbPrefixed) {
            int nextAddr = (pc + 1) & 0xFFFF;
            int cbOpcode = memory.peekByte(nextAddr) & 0xFF;
            operation = CB_PREFIXED.get(cbOpcode);
            instructionBytes = operation != null ? Math.max(2, operation.getBytes()) : 2;
            if (operation == null) {
                return new DecodedInstruction(pc, instructionBytes,
                        String.format("??? (CB %02X)", cbOpcode), List.of(opcode, cbOpcode), opcode, cbOpcode);
            }
            return buildDecodedInstruction(memory, pc, opcode, cbOpcode, operation, instructionBytes, true);
        }

        operation = UNPREFIXED.get(opcode);
        instructionBytes = operation != null ? Math.max(1, operation.getBytes()) : 1;
        if (operation == null) {
            return new DecodedInstruction(pc, instructionBytes,
                    String.format("??? ($%02X)", opcode), List.of(opcode), opcode);
        }
        return buildDecodedInstruction(memory, pc, opcode, 0, operation, instructionBytes, false);
    }

    private static DecodedInstruction buildDecodedInstruction(Memory memory,
            int address,
            int opcode,
            int cbOpcode,
            Operation operation,
            int instructionBytes,
            boolean cbPrefixed) {
        List<Integer> rawBytes = captureBytes(memory, address, instructionBytes);
        String mnemonic = operation.getMnemonic();
        String operandText = formatOperands(memory, address, instructionBytes, operation, cbPrefixed);
        StringBuilder sb = new StringBuilder();
        sb.append(mnemonic);
        if (!operandText.isEmpty()) {
            sb.append(' ')
                    .append(operandText);
        }
        if (cbPrefixed) {
            return new DecodedInstruction(address, instructionBytes, sb.toString(), rawBytes, opcode, cbOpcode);
        }
        return new DecodedInstruction(address, instructionBytes, sb.toString(), rawBytes, opcode);
    }

    private static List<Integer> captureBytes(Memory memory, int start, int length) {
        List<Integer> bytes = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            int addr = (start + i) & 0xFFFF;
            bytes.add(memory.peekByte(addr) & 0xFF);
        }
        return bytes;
    }

    private static String formatOperands(Memory memory,
            int address,
            int instructionBytes,
            Operation operation,
            boolean cbPrefixed) {
        List<Map<String, Object>> operands = operation.getOperands();
        if (operands == null || operands.isEmpty()) {
            // Special case: STOP has a padding byte that is usually zero. We omit it from
            // text.
            if ("STOP".equals(operation.getMnemonic()) && instructionBytes > 1) {
                int padding = readByte(memory, address, 1);
                if (padding != 0) {
                    return String.format("$%02X", padding);
                }
            }
            return "";
        }

        ValueCursor cursor = new ValueCursor(cbPrefixed ? 2 : 1, instructionBytes);
        List<String> rendered = new ArrayList<>(operands.size());
        for (Map<String, Object> operand : operands) {
            rendered.add(formatOperand(memory, address, cursor, operand));
        }
        return String.join(", ", rendered);
    }

    private static String formatOperand(Memory memory,
            int address,
            ValueCursor cursor,
            Map<String, Object> operand) {
        String name = (String) operand.get("name");
        boolean isImmediate = Boolean.TRUE.equals(operand.get("immediate"));
        boolean isMemory = Boolean.TRUE.equals(operand.get("memory"));

        if (isImmediate) {
            if (CONDITIONS.contains(name)) {
                return name;
            }
            if (name != null && name.endsWith("H") && name.length() == 3 && Character.isDigit(name.charAt(0))) {
                return "$" + name.substring(0, 2);
            }
            return formatImmediateValue(memory, address, cursor, name);
        }

        if (isMemory) {
            return formatMemoryOperand(memory, address, cursor, name);
        }

        // 8-bit register or symbolic operand
        return name;
    }

    private static String formatImmediateValue(Memory memory,
            int address,
            ValueCursor cursor,
            String name) {
        switch (name) {
            case "d8": {
                int value = readByte(memory, address, cursor.consume(1));
                return String.format("$%02X", value);
            }
            case "d16":
            case "a16": {
                int value = readWord(memory, address, cursor.consume(2));
                return String.format("$%04X", value);
            }
            case "a8": {
                int value = readByte(memory, address, cursor.consume(1));
                return String.format("$%02X", value);
            }
            case "r8": {
                int offset = (byte) readByte(memory, address, cursor.consume(1));
                int nextAddress = (address + cursor.getInstructionBytes()) & 0xFFFF;
                int target = (nextAddress + offset) & 0xFFFF;
                return String.format("$%04X", target);
            }
            case "SP+r8": {
                int value = (byte) readByte(memory, address, cursor.consume(1));
                return "SP" + formatSigned(value);
            }
            default:
                return name;
        }
    }

    private static String formatMemoryOperand(Memory memory,
            int address,
            ValueCursor cursor,
            String name) {
        if (name == null) {
            return "(???)";
        }
        switch (name) {
            case "HL":
            case "BC":
            case "DE":
            case "SP":
                return "(" + name + ")";
            case "HL+":
            case "HL-":
                return "(" + name + ")";
            case "C":
                return "($FF00+C)";
            case "a8": {
                int value = readByte(memory, address, cursor.consume(1));
                return String.format("($FF00+$%02X)", value);
            }
            case "a16": {
                int value = readWord(memory, address, cursor.consume(2));
                return String.format("($%04X)", value);
            }
            default:
                return "(" + name + ")";
        }
    }

    private static int readByte(Memory memory, int base, int offset) {
        int address = (base + offset) & 0xFFFF;
        return memory.peekByte(address) & 0xFF;
    }

    private static int readWord(Memory memory, int base, int offset) {
        int lo = readByte(memory, base, offset);
        int hi = readByte(memory, base, offset + 1);
        return (hi << 8) | lo;
    }

    private static String formatSigned(int value) {
        if (value >= 0) {
            return String.format("+$%02X", value);
        }
        return String.format("-$%02X", (-value) & 0xFF);
    }

    private static Map<Integer, Operation> flatten(EnumMap<OperationType, Map<Integer, Operation>> grouped) {
        Map<Integer, Operation> table = new HashMap<>();
        for (Map<Integer, Operation> bucket : grouped.values()) {
            for (Map.Entry<Integer, Operation> entry : bucket.entrySet()) {
                table.put(entry.getKey() & 0xFF, entry.getValue());
            }
        }
        return table;
    }

    private static final class ValueCursor {
        private int offset;
        private final int instructionBytes;

        private ValueCursor(int initialOffset, int instructionBytes) {
            this.offset = initialOffset;
            this.instructionBytes = instructionBytes;
        }

        int consume(int amount) {
            int current = offset;
            offset += amount;
            return current;
        }

        int getInstructionBytes() {
            return instructionBytes;
        }
    }

    public static final class DecodedInstruction {
        private final int address;
        private final int length;
        private final String text;
        private final List<Integer> rawBytes;
        private final int opcode;
        private final Integer cbOpcode;

        private DecodedInstruction(int address, int length, String text, List<Integer> rawBytes, int opcode) {
            this(address, length, text, rawBytes, opcode, null);
        }

        private DecodedInstruction(int address,
                int length,
                String text,
                List<Integer> rawBytes,
                int opcode,
                Integer cbOpcode) {
            this.address = address & 0xFFFF;
            this.length = length;
            this.text = text;
            this.rawBytes = List.copyOf(rawBytes);
            this.opcode = opcode;
            this.cbOpcode = cbOpcode;
        }

        public int getAddress() {
            return address;
        }

        public int getLength() {
            return length;
        }

        public String getText() {
            return text;
        }

        public List<Integer> getRawBytes() {
            return rawBytes;
        }

        public int getOpcode() {
            return opcode;
        }

        public Integer getCbOpcode() {
            return cbOpcode;
        }
    }
}
