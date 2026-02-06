package gbc.model.cpu;

import java.util.List;
import java.util.Map;

import gbc.model.memory.Memory;

@FunctionalInterface
public interface OperationExecutor {
    void execute(Registers registers, Memory memory, List<Map<String, Object>> operands);

    default void execute(ExecutionContext context, List<Map<String, Object>> operands) {
        execute(context.registers(), context.memory(), operands);
    }

    record ExecutionContext(Registers registers, Memory memory, CPU cpu) {
        public void tickInternalMcycle() {
            if (cpu != null) {
                cpu.tickInternalMcycle();
            }
        }
    }
}
