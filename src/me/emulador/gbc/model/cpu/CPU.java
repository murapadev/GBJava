package me.emulador.gbc.model.cpu;
import me.emulador.gbc.model.memory.Memory;

/**
 * Represents the CPU of the Game Boy Color.
 */
public class CPU {
    //Registers
    private Registers registers;
    private Memory memory;

    public CPU(Memory memory) {
        this.memory = memory;
        this.registers = new Registers();
        reset();
    }

    public CPU(Memory memory, Registers registers) {
        this.memory = memory;
        this.registers = registers;
    }


    /**
     * Reset the CPU to its initial state.
     */
    public void reset() {
        registers.setPC((short) 0x0100);
        registers.setSP((short) 0xFFFE);
        registers.setA((byte) 0x00);
        registers.setB((byte) 0x00);
        registers.setC((byte) 0x00);
        registers.setD((byte) 0x00);
        registers.setE((byte) 0x00);
        registers.setF((byte) 0x00);
        registers.setH((byte) 0x00);
        registers.setL((byte) 0x00);
    }

    /**
     * Execute an instruction.
     */
    public void executeInstruction(byte opcode) {
        // Placeholder: Aquí iría la lógica para decodificar y ejecutar una instrucción.
        // Por ahora, solo se incrementa el PC.

        System.out.println("PC: " + registers.getPC() + " Opcode: " + opcode);


        registers.setPC((short) (registers.getPC() + 1));
    }

    /**
     * Push a short value onto the stack.
     *
     * @param value The short value to be pushed.
     */
    public void push(short value) {
        registers.setSP((short) (registers.getSP() - 2));
        memory.writeShort(registers.getSP(), value);
    }

    /**
     * Pop a short value from the stack.
     *
     * @return The short value popped from the stack.
     */
    public short pop() {
        short value = memory.readShort(registers.getSP());
        registers.setSP((short) (registers.getSP() + 2));
        return value;
    }

    // Getters y setters para los registros y otros campos según sea necesario.
    public Registers getRegisters() {
        return registers;
    }

    public void setRegisters(Registers registers) {
        this.registers = registers;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }
}