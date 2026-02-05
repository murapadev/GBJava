package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface OperationExecutor {
    // TODO: Add a cycle-accurate execution context (internal M-cycles, micro-ops).
    void execute(Registers registers, Memory memory, List<Map<String, Object>> operands);
}
