package gbc.model.trace;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

import gbc.model.cpu.CPU;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;

public class TraceGenerator {

    private static final int MAX_RING_BUFFER_SIZE = 10000; // Keep last 10k instructions
    private static final int MAX_TOTAL_LINES = 1_000_000; // Max 1M lines per file

    private final String romName;
    private final CPU cpu;
    private final Memory memory;
    private final boolean useRingBuffer;
    private final Deque<String> ringBuffer;
    private BufferedWriter writer;
    private long lineCount = 0;
    private boolean closed = false;

    public TraceGenerator(String romName, CPU cpu, Memory memory) {
        this.romName = romName;
        this.cpu = cpu;
        this.memory = memory;
        this.useRingBuffer = Boolean.getBoolean("gbc.trace.ringbuffer");

        if (useRingBuffer) {
            this.ringBuffer = new ArrayDeque<>(MAX_RING_BUFFER_SIZE);
            this.writer = null;
        } else {
            this.ringBuffer = null;
            try {
                Path logDir = Paths.get("..", "logs");
                if (!Files.exists(logDir)) {
                    Files.createDirectories(logDir);
                }
                Path traceFile = logDir.resolve(sanitizeFilename(romName) + ".log");
                this.writer = Files.newBufferedWriter(traceFile);
                writeHeader();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create trace file", e);
            }
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private void writeHeader() throws IOException {
        if (writer != null) {
            writer.write("# Trace for ROM: " + romName + "\n");
            writer.write(
                    "# Format: A:XX F:XX B:XX C:XX D:XX E:XX H:XX L:XX SP:XXXX PC:XXXX | [opcode bytes] MNEMONIC | flags | cycles\n");
            writer.write("# Flags: Z=Zero, N=Subtract, H=HalfCarry, C=Carry, IME=InterruptMasterEnable, HALT=Halted\n");
            writer.write("# ==================================================================================\n");
        }
    }

    public void logInstruction() {
        if (closed || lineCount >= MAX_TOTAL_LINES) {
            return;
        }

        Registers r = cpu.getRegisters();
        int pc = r.getPC();

        // Read opcode and operands
        int opcode = memory.peekByte(pc) & 0xFF;
        String opcodeStr;
        String mnemonic = "???";
        int instrBytes = 1;

        if (opcode == 0xCB) {
            int cbOp = memory.peekByte((pc + 1) & 0xFFFF) & 0xFF;
            opcodeStr = String.format("CB %02X", cbOp);
            mnemonic = decodeCBInstruction(cbOp);
            instrBytes = 2;
        } else {
            opcodeStr = String.format("%02X", opcode);
            mnemonic = decodeInstruction(opcode);
            instrBytes = getInstructionLength(opcode);

            if (instrBytes == 2) {
                int op1 = memory.peekByte((pc + 1) & 0xFFFF) & 0xFF;
                opcodeStr = String.format("%02X %02X", opcode, op1);
                mnemonic = String.format(mnemonic, op1);
            } else if (instrBytes == 3) {
                int op1 = memory.peekByte((pc + 1) & 0xFFFF) & 0xFF;
                int op2 = memory.peekByte((pc + 2) & 0xFFFF) & 0xFF;
                opcodeStr = String.format("%02X %02X %02X", opcode, op1, op2);
                int imm16 = op1 | (op2 << 8);
                mnemonic = String.format(mnemonic, imm16);
            }
        }

        // Format flags
        String flags = String.format("Z:%d N:%d H:%d C:%d IME:%d HALT:%d",
                r.isZ() ? 1 : 0,
                r.isN() ? 1 : 0,
                r.isH() ? 1 : 0,
                r.isC() ? 1 : 0,
                cpu.isIme() ? 1 : 0,
                cpu.isHalted() ? 1 : 0);

        // Read key hardware registers
        int div = memory.peekByte(0xFF04) & 0xFF;
        int tima = memory.peekByte(0xFF05) & 0xFF;
        int iff = memory.peekByte(0xFF0F) & 0xFF;
        int ie = memory.peekByte(0xFFFF) & 0xFF;

        String trace = String.format(
                "A:%02X F:%02X B:%02X C:%02X D:%02X E:%02X H:%02X L:%02X SP:%04X PC:%04X | [%s] %-20s | %s | DIV:%02X TIMA:%02X IF:%02X IE:%02X",
                r.getRegister("A") & 0xFF, r.getRegister("F") & 0xFF,
                r.getRegister("B") & 0xFF, r.getRegister("C") & 0xFF,
                r.getRegister("D") & 0xFF, r.getRegister("E") & 0xFF,
                r.getRegister("H") & 0xFF, r.getRegister("L") & 0xFF,
                r.getSP(), pc, opcodeStr, mnemonic, flags, div, tima, iff, ie);

        if (useRingBuffer) {
            if (ringBuffer.size() >= MAX_RING_BUFFER_SIZE) {
                ringBuffer.removeFirst();
            }
            ringBuffer.addLast(trace);
        } else {
            try {
                writer.write(trace);
                writer.newLine();
                lineCount++;

                // Flush periodically
                if (lineCount % 1000 == 0) {
                    writer.flush();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public void dumpRingBuffer() {
        if (useRingBuffer && !ringBuffer.isEmpty()) {
            try {
                Path logDir = Paths.get("..", "logs");
                if (!Files.exists(logDir)) {
                    Files.createDirectories(logDir);
                }
                Path traceFile = logDir.resolve(sanitizeFilename(romName) + "_last" + ringBuffer.size() + ".log");
                try (BufferedWriter w = Files.newBufferedWriter(traceFile)) {
                    w.write("# Last " + ringBuffer.size() + " instructions before failure\n");
                    w.write("# ROM: " + romName + "\n");
                    w.write("# ==================================================================================\n");
                    for (String line : ringBuffer) {
                        w.write(line);
                        w.newLine();
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public void close() {
        if (closed)
            return;
        closed = true;

        if (useRingBuffer) {
            dumpRingBuffer();
        } else if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private String decodeInstruction(int opcode) {
        return switch (opcode) {
            case 0x00 -> "NOP";
            case 0x01 -> "LD BC,$%04X";
            case 0x02 -> "LD (BC),A";
            case 0x03 -> "INC BC";
            case 0x04 -> "INC B";
            case 0x05 -> "DEC B";
            case 0x06 -> "LD B,$%02X";
            case 0x07 -> "RLCA";
            case 0x08 -> "LD ($%04X),SP";
            case 0x09 -> "ADD HL,BC";
            case 0x0A -> "LD A,(BC)";
            case 0x0B -> "DEC BC";
            case 0x0C -> "INC C";
            case 0x0D -> "DEC C";
            case 0x0E -> "LD C,$%02X";
            case 0x0F -> "RRCA";
            case 0x10 -> "STOP";
            case 0x11 -> "LD DE,$%04X";
            case 0x12 -> "LD (DE),A";
            case 0x13 -> "INC DE";
            case 0x14 -> "INC D";
            case 0x15 -> "DEC D";
            case 0x16 -> "LD D,$%02X";
            case 0x17 -> "RLA";
            case 0x18 -> "JR $%02X";
            case 0x19 -> "ADD HL,DE";
            case 0x1A -> "LD A,(DE)";
            case 0x1B -> "DEC DE";
            case 0x1C -> "INC E";
            case 0x1D -> "DEC E";
            case 0x1E -> "LD E,$%02X";
            case 0x1F -> "RRA";
            case 0x20 -> "JR NZ,$%02X";
            case 0x21 -> "LD HL,$%04X";
            case 0x22 -> "LD (HL+),A";
            case 0x23 -> "INC HL";
            case 0x24 -> "INC H";
            case 0x25 -> "DEC H";
            case 0x26 -> "LD H,$%02X";
            case 0x27 -> "DAA";
            case 0x28 -> "JR Z,$%02X";
            case 0x29 -> "ADD HL,HL";
            case 0x2A -> "LD A,(HL+)";
            case 0x2B -> "DEC HL";
            case 0x2C -> "INC L";
            case 0x2D -> "DEC L";
            case 0x2E -> "LD L,$%02X";
            case 0x2F -> "CPL";
            case 0x30 -> "JR NC,$%02X";
            case 0x31 -> "LD SP,$%04X";
            case 0x32 -> "LD (HL-),A";
            case 0x33 -> "INC SP";
            case 0x34 -> "INC (HL)";
            case 0x35 -> "DEC (HL)";
            case 0x36 -> "LD (HL),$%02X";
            case 0x37 -> "SCF";
            case 0x38 -> "JR C,$%02X";
            case 0x39 -> "ADD HL,SP";
            case 0x3A -> "LD A,(HL-)";
            case 0x3B -> "DEC SP";
            case 0x3C -> "INC A";
            case 0x3D -> "DEC A";
            case 0x3E -> "LD A,$%02X";
            case 0x3F -> "CCF";
            case 0x76 -> "HALT";
            case 0xC3 -> "JP $%04X";
            case 0xC9 -> "RET";
            case 0xCD -> "CALL $%04X";
            case 0xE0 -> "LDH ($FF%02X),A";
            case 0xE2 -> "LD (C),A";
            case 0xEA -> "LD ($%04X),A";
            case 0xF0 -> "LDH A,($FF%02X)";
            case 0xF2 -> "LD A,(C)";
            case 0xF3 -> "DI";
            case 0xFA -> "LD A,($%04X)";
            case 0xFB -> "EI";
            default -> {
                int hi = (opcode >> 4) & 0xF;
                yield switch (hi) {
                    case 0x4, 0x5, 0x6, 0x7 -> "LD/MOV";
                    case 0x8 -> "ALU";
                    case 0x9 -> "SUB/SBC";
                    case 0xA -> "AND/XOR";
                    case 0xB -> "OR/CP";
                    case 0xC, 0xD -> "CTRL";
                    default -> "???";
                };
            }
        };
    }

    private String decodeCBInstruction(int cbOp) {
        int bit = (cbOp >> 3) & 0x7;
        int reg = cbOp & 0x7;
        String[] regs = { "B", "C", "D", "E", "H", "L", "(HL)", "A" };

        if (cbOp < 0x40) {
            return switch (cbOp >> 3) {
                case 0 -> "RLC " + regs[reg];
                case 1 -> "RRC " + regs[reg];
                case 2 -> "RL " + regs[reg];
                case 3 -> "RR " + regs[reg];
                case 4 -> "SLA " + regs[reg];
                case 5 -> "SRA " + regs[reg];
                case 6 -> "SWAP " + regs[reg];
                case 7 -> "SRL " + regs[reg];
                default -> "???";
            };
        } else if (cbOp < 0x80) {
            return "BIT " + bit + "," + regs[reg];
        } else if (cbOp < 0xC0) {
            return "RES " + bit + "," + regs[reg];
        } else {
            return "SET " + bit + "," + regs[reg];
        }
    }

    private int getInstructionLength(int opcode) {
        return switch (opcode) {
            case 0x01, 0x08, 0x11, 0x21, 0x31, 0xC2, 0xC3, 0xC4, 0xCA, 0xCC, 0xCD,
                    0xD2, 0xD4, 0xDA, 0xDC, 0xEA, 0xFA ->
                3;
            case 0x06, 0x0E, 0x16, 0x1E, 0x18, 0x20, 0x26, 0x28, 0x2E, 0x30, 0x36,
                    0x38, 0x3E, 0xC6, 0xCE, 0xD6, 0xDE, 0xE0, 0xE6, 0xEE, 0xF0, 0xF6,
                    0xFE ->
                2;
            case 0xCB -> 2;
            default -> 1;
        };
    }
}
