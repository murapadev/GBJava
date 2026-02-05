package gbc.model.cpu;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class OpcodeCoverageTest {

    @Test
    void unprefixedOpcodesHaveExecutors() {
        Memory memory = new Memory();
        CPU cpu = new CPU(memory);
        OperationsLoader loader = new OperationsLoader(cpu.getInterruptions(), cpu);

        for (int opcode = 0; opcode <= 0xFF; opcode++) {
            Operation op = loader.getOperation(opcode);
            assertNotNull(op, String.format("Missing unprefixed opcode 0x%02X", opcode));
            assertNotNull(op.getExecutor(), String.format("Missing executor for unprefixed opcode 0x%02X", opcode));
        }
    }

    @Test
    void cbOpcodesHaveExecutors() {
        Memory memory = new Memory();
        CPU cpu = new CPU(memory);
        OperationsLoader loader = new OperationsLoader(cpu.getInterruptions(), cpu);

        for (int opcode = 0; opcode <= 0xFF; opcode++) {
            Operation op = loader.getCbOperation(opcode);
            assertNotNull(op, String.format("Missing CB opcode 0x%02X", opcode));
            assertNotNull(op.getExecutor(), String.format("Missing executor for CB opcode 0x%02X", opcode));
        }
    }
}
