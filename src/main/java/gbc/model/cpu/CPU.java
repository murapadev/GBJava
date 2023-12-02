package gbc.model.cpu;
import gbc.model.memory.Memory;

//Json

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the CPU of the Game Boy Coloregisters.
 */
public class CPU {
    //Registers
    private Registers registers;
    private Memory memory;
	private Interruptions interruptions;
	private int cycles;

    private final Map<Byte, Operation> operations = new HashMap<>();

    private final ArrayList<String> opLog = new ArrayList<String>();

    public CPU(Memory memory) {
        this.memory = memory;
        this.registers = new Registers();
        this.interruptions = new Interruptions(this.memory, this.registers);
        reset();

        operations.put((byte) 0x00, this::nop);
        operations.put((byte) 0x10, () -> rl((byte) 0x10));
        operations.put((byte) 0x21, () -> ld((byte) 0x21));
        operations.put((byte) 0x3E, () -> ld((byte) 0x3E));
        operations.put((byte) 0xAF, () -> res((byte) 0xAF));
        operations.put((byte) 0xC3, () -> jp((byte) 0xC3));
        operations.put((byte) 0xD8, this::ret);
        operations.put((byte) 0xDF, () -> rst((byte) 0xDF));
        operations.put((byte) 0xF0, () -> set((byte) 0xF0));
        operations.put((byte) 0xF8, () -> set((byte) 0xF8));


    }

    public CPU(Memory memory, Registers registers) {
        this.memory = memory;
        this.registers = registers;
    }


    /**
     * Reset the CPU to its initial state.
     */
    public void reset() {
        registers.setPC((char) 0x0100);
        registers.setSP((char) 0xFFFE);
        registers.setAF((char) 0x01B0);
        registers.setBC((char) 0x0013);
        registers.setDE((char) 0x00D8);
        registers.setHL((char) 0x014D);
        memory.reset();
        interruptions.setEnable((byte) 0x00);
        interruptions.setFlags((byte) 0x00);
        interruptions.setMaster((byte) 0x00);

		cycles = 0;
    }

	public void executeCycle() {
		byte opcode = memory.readByte(registers.getPC());
		execute(opcode);
	}

    /**
     * Execute an instruction.
     */
    public void execute(byte opcode) {
        Operation operation = operations.get(opcode);
        this.opLog.add("0x" + Integer.toHexString(registers.getPC()) + ": " + Integer.toHexString(opcode) + "\n");
        if (operation != null) {
            operation.execute();
        } else {
            throw new IllegalArgumentException("Invalid opcode: " + opcode);
        }
    }

    public void nop() {
        registers.setPC((char) (registers.getPC() + 1));
        this.cycles += 2;
    }


    public void ld(byte opcode) {
        byte value = memory.readByte((char) (registers.getPC() + 1));
        String register = getRegister(opcode);
        registers.setRegister(register, value);
        registers.setPC((char) (registers.getPC() + 2));
        this.cycles += 2;
    }

    public void jp(byte opcode) {
        char address = memory.readChar((char) (registers.getPC() + 1));
        registers.setPC(address);
        this.cycles += 4;
    }

    public void res(byte opcode) {
        String register = getRegister(opcode);
        registers.setRegister(register, (byte) 0x00);
        registers.setPC((char) (registers.getPC() + 1));
        this.cycles += 2;
    }

    public void rst(byte opcode) {
        char address = memory.readChar((char) (registers.getPC() + 1));
        push(registers.getPC());
        registers.setPC(address);
        this.cycles += 4;
    }

    public void rl(byte opcode) {
        String register = getRegister(opcode);
        byte value = registers.getRegister(register);
        byte bit7 = (byte) ((value & 0b10000000) >> 7);
        value = (byte) ((value << 1) | bit7);
        registers.setRegister(register, value);
        registers.setPC((char) (registers.getPC() + 1));
        this.cycles += 2;
    }

    public void ret() {
    	registers.setPC(pop());
        	this.cycles += 4;
    }

    public void set(byte opcode) {
        String register = getRegister(opcode);
        byte value = registers.getRegister(register);
        byte bit7 = (byte) ((value & 0b10000000) >> 7);
        value = (byte) ((value << 1) | bit7);
        registers.setRegister(register, value);
        registers.setPC((char) (registers.getPC() + 1));
        this.cycles += 2;
    }

    /**
     * Push a char value onto the stack.
     *
     * @param value The char value to be pushed.
     */
    public void push(char value) {
        registers.setSP((char) (registers.getSP() - 2));
        memory.writeChar(registers.getSP(), value);

    }

    /**
     * Pop a char value from the stack.
     *
     * @return The char value popped from the stack.
     */
    public char pop() {
        char value = memory.readChar(registers.getSP());
        registers.setSP((char) (registers.getSP() + 2));
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

	public Interruptions getInterruptions() {
		return this.interruptions;
	}

	public int getCycles() {
		return cycles;
	}

	public void setCycles(int i) {
		cycles = i;
	}

    private String getRegister(byte opcode) {
        byte register = (byte) ((opcode & 0b00111000) >> 3);
        return switch (register) {
            case 0 -> "B";
            case 1 -> "C";
            case 2 -> "D";
            case 3 -> "E";
            case 4 -> "H";
            case 5 -> "L";
            case 6 -> "HL";
            case 7 -> "A";
            default -> throw new IllegalArgumentException("Invalid register: " + register);
        };
    }

    public ArrayList<String> getOpLog() {
    	return new ArrayList<String>(opLog);
    }

    @Override
    public String toString() {
        return "CPU{" +
                "registers=" + registers +
                ", memory=" + memory +
                '}';


    }
}
    