package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface OperationExecutor {
    void execute(Registers registers, Memory memory, List<Map<String, Object>> operands);
}
