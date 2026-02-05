package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.List;
import java.util.Map;

public class Operation {
    // TODO: Enforce non-null executor/operand metadata for all defined opcodes.
    private OperationExecutor executor;
    private String mnemonic;
    private int bytes;
    private List<Integer> cycles;
    private boolean immediate;
    private Map<String, String> flags;
    private List<Map<String, Object>> operands;
    private OperationType type = OperationType.MISC;

    public Operation() {
        // Default constructor for reflective creation
    }

    public Operation(OperationExecutor executor, String mnemonic, int bytes, List<Integer> cycles, boolean immediate, Map<String, String> flags, List<Map<String, Object>> operands) {
        this.executor = executor;
        this.mnemonic = mnemonic;
        this.bytes = bytes;
        this.cycles = cycles;
        this.immediate = immediate;
        this.flags = flags;
        this.operands = operands;
    }

    public void perform(Registers registers, Memory memory) {
        executor.execute(registers, memory, operands);
    }

    // getters and setters for the new fields...




    public OperationExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(OperationExecutor executor) {
        this.executor = executor;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    public int getBytes() {
        return bytes;
    }

    public void setBytes(int bytes) {
        this.bytes = bytes;
    }

    public List<Integer> getCycles() {
        return cycles;
    }

    public void setCycles(List<Integer> cycles) {
        this.cycles = cycles;
    }

    public boolean isImmediate() {
        return immediate;
    }

    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }

    public Map<String, String> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, String> flags) {
        this.flags = flags;
    }

    public List<Map<String, Object>> getOperands() {
        return operands;
    }

    public void setOperands(List<Map<String, Object>> operands) {
        this.operands = operands;
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type == null ? OperationType.MISC : type;
    }
}
