import java.util.*;

public class opcode_audit {
    public static void main(String[] args) {
        // Game Boy CPU complete instruction set
        Set<Integer> implementedOpcodes = new HashSet<>();

        // From OperationDefinitions.java - Regular opcodes
        // NOP
        implementedOpcodes.add(0x00);

        // LD operations - 8-bit immediate
        implementedOpcodes.add(0x06); // LD B,d8
        implementedOpcodes.add(0x0E); // LD C,d8
        implementedOpcodes.add(0x16); // LD D,d8
        implementedOpcodes.add(0x1E); // LD E,d8
        implementedOpcodes.add(0x26); // LD H,d8
        implementedOpcodes.add(0x2E); // LD L,d8
        implementedOpcodes.add(0x36); // LD (HL),d8
        implementedOpcodes.add(0x3E); // LD A,d8

        // LD operations - 16-bit immediate
        implementedOpcodes.add(0x01); // LD BC,d16
        implementedOpcodes.add(0x11); // LD DE,d16
        implementedOpcodes.add(0x21); // LD HL,d16
        implementedOpcodes.add(0x31); // LD SP,d16

        // LD operations - register to register (8x8 = 64 opcodes from 0x40-0x7F)
        for (int i = 0x40; i <= 0x7F; i++) {
            if (i != 0x76) { // 0x76 is HALT, not LD
                implementedOpcodes.add(i);
            }
        }

        // Special LD operations
        implementedOpcodes.add(0x02); // LD (BC),A
        implementedOpcodes.add(0x12); // LD (DE),A
        implementedOpcodes.add(0x22); // LD (HL+),A
        implementedOpcodes.add(0x32); // LD (HL-),A
        implementedOpcodes.add(0x0A); // LD A,(BC)
        implementedOpcodes.add(0x1A); // LD A,(DE)
        implementedOpcodes.add(0x2A); // LD A,(HL+)
        implementedOpcodes.add(0x3A); // LD A,(HL-)
        implementedOpcodes.add(0xE0); // LDH (a8),A
        implementedOpcodes.add(0xF0); // LDH A,(a8)
        implementedOpcodes.add(0xE2); // LDH (C),A
        implementedOpcodes.add(0xF2); // LDH A,(C)
        implementedOpcodes.add(0xFA); // LD A,(a16)
        implementedOpcodes.add(0xEA); // LD (a16),A
        implementedOpcodes.add(0x08); // LD (a16),SP
        implementedOpcodes.add(0xF9); // LD SP,HL
        implementedOpcodes.add(0xF8); // LD HL,SP+r8

        // Arithmetic operations
        for (int i = 0x80; i <= 0x87; i++)
            implementedOpcodes.add(i); // ADD A,r
        implementedOpcodes.add(0xC6); // ADD A,d8
        for (int i = 0x88; i <= 0x8F; i++)
            implementedOpcodes.add(i); // ADC A,r
        implementedOpcodes.add(0xCE); // ADC A,d8
        for (int i = 0x90; i <= 0x97; i++)
            implementedOpcodes.add(i); // SUB r
        implementedOpcodes.add(0xD6); // SUB d8
        for (int i = 0x98; i <= 0x9F; i++)
            implementedOpcodes.add(i); // SBC A,r
        implementedOpcodes.add(0xDE); // SBC A,d8

        // 16-bit arithmetic
        implementedOpcodes.add(0x09); // ADD HL,BC
        implementedOpcodes.add(0x19); // ADD HL,DE
        implementedOpcodes.add(0x29); // ADD HL,HL
        implementedOpcodes.add(0x39); // ADD HL,SP
        implementedOpcodes.add(0xE8); // ADD SP,r8

        // Logic operations
        for (int i = 0xA0; i <= 0xA7; i++)
            implementedOpcodes.add(i); // AND r
        implementedOpcodes.add(0xE6); // AND d8
        for (int i = 0xA8; i <= 0xAF; i++)
            implementedOpcodes.add(i); // XOR r
        implementedOpcodes.add(0xEE); // XOR d8
        for (int i = 0xB0; i <= 0xB7; i++)
            implementedOpcodes.add(i); // OR r
        implementedOpcodes.add(0xF6); // OR d8
        for (int i = 0xB8; i <= 0xBF; i++)
            implementedOpcodes.add(i); // CP r
        implementedOpcodes.add(0xFE); // CP d8

        // Inc/Dec operations
        implementedOpcodes.add(0x04); // INC B
        implementedOpcodes.add(0x0C); // INC C
        implementedOpcodes.add(0x14); // INC D
        implementedOpcodes.add(0x1C); // INC E
        implementedOpcodes.add(0x24); // INC H
        implementedOpcodes.add(0x2C); // INC L
        implementedOpcodes.add(0x34); // INC (HL)
        implementedOpcodes.add(0x3C); // INC A
        implementedOpcodes.add(0x05); // DEC B
        implementedOpcodes.add(0x0D); // DEC C
        implementedOpcodes.add(0x15); // DEC D
        implementedOpcodes.add(0x1D); // DEC E
        implementedOpcodes.add(0x25); // DEC H
        implementedOpcodes.add(0x2D); // DEC L
        implementedOpcodes.add(0x35); // DEC (HL)
        implementedOpcodes.add(0x3D); // DEC A

        // 16-bit Inc/Dec
        implementedOpcodes.add(0x03); // INC BC
        implementedOpcodes.add(0x13); // INC DE
        implementedOpcodes.add(0x23); // INC HL
        implementedOpcodes.add(0x33); // INC SP
        implementedOpcodes.add(0x0B); // DEC BC
        implementedOpcodes.add(0x1B); // DEC DE
        implementedOpcodes.add(0x2B); // DEC HL
        implementedOpcodes.add(0x3B); // DEC SP

        // Stack operations
        implementedOpcodes.add(0xC5); // PUSH BC
        implementedOpcodes.add(0xD5); // PUSH DE
        implementedOpcodes.add(0xE5); // PUSH HL
        implementedOpcodes.add(0xF5); // PUSH AF
        implementedOpcodes.add(0xC1); // POP BC
        implementedOpcodes.add(0xD1); // POP DE
        implementedOpcodes.add(0xE1); // POP HL
        implementedOpcodes.add(0xF1); // POP AF

        // Jump operations
        implementedOpcodes.add(0x18); // JR r8
        implementedOpcodes.add(0x20); // JR NZ,r8
        implementedOpcodes.add(0x28); // JR Z,r8
        implementedOpcodes.add(0x30); // JR NC,r8
        implementedOpcodes.add(0x38); // JR C,r8
        implementedOpcodes.add(0xC3); // JP a16
        implementedOpcodes.add(0xC2); // JP NZ,a16
        implementedOpcodes.add(0xCA); // JP Z,a16
        implementedOpcodes.add(0xD2); // JP NC,a16
        implementedOpcodes.add(0xDA); // JP C,a16
        implementedOpcodes.add(0xE9); // JP (HL)

        // Call operations
        implementedOpcodes.add(0xCD); // CALL a16
        implementedOpcodes.add(0xC4); // CALL NZ,a16
        implementedOpcodes.add(0xCC); // CALL Z,a16
        implementedOpcodes.add(0xD4); // CALL NC,a16
        implementedOpcodes.add(0xDC); // CALL C,a16

        // Return operations
        implementedOpcodes.add(0xC9); // RET
        implementedOpcodes.add(0xC0); // RET NZ
        implementedOpcodes.add(0xC8); // RET Z
        implementedOpcodes.add(0xD0); // RET NC
        implementedOpcodes.add(0xD8); // RET C
        implementedOpcodes.add(0xD9); // RETI

        // RST operations
        implementedOpcodes.add(0xC7); // RST 00H
        implementedOpcodes.add(0xCF); // RST 08H
        implementedOpcodes.add(0xD7); // RST 10H
        implementedOpcodes.add(0xDF); // RST 18H
        implementedOpcodes.add(0xE7); // RST 20H
        implementedOpcodes.add(0xEF); // RST 28H
        implementedOpcodes.add(0xF7); // RST 30H
        implementedOpcodes.add(0xFF); // RST 38H

        // Bit manipulation
        implementedOpcodes.add(0x07); // RLCA
        implementedOpcodes.add(0x0F); // RRCA
        implementedOpcodes.add(0x17); // RLA
        implementedOpcodes.add(0x1F); // RRA

        // Misc operations
        implementedOpcodes.add(0x37); // SCF
        implementedOpcodes.add(0x3F); // CCF
        implementedOpcodes.add(0x2F); // CPL
        implementedOpcodes.add(0x27); // DAA
        implementedOpcodes.add(0x76); // HALT
        implementedOpcodes.add(0x10); // STOP
        implementedOpcodes.add(0xF3); // DI
        implementedOpcodes.add(0xFB); // EI
        implementedOpcodes.add(0xCB); // CB prefix

        // Invalid opcodes (should not be executed but we handle them gracefully)
        implementedOpcodes.add(0xD3); // INVALID
        implementedOpcodes.add(0xDB); // INVALID
        implementedOpcodes.add(0xDD); // INVALID
        implementedOpcodes.add(0xE3); // INVALID
        implementedOpcodes.add(0xE4); // INVALID
        implementedOpcodes.add(0xEB); // INVALID
        implementedOpcodes.add(0xEC); // INVALID
        implementedOpcodes.add(0xED); // INVALID
        implementedOpcodes.add(0xF4); // INVALID
        implementedOpcodes.add(0xFC); // INVALID
        implementedOpcodes.add(0xFD); // INVALID

        // Check which opcodes are missing
        System.out.println("Missing regular opcodes:");
        for (int i = 0; i <= 0xFF; i++) {
            if (!implementedOpcodes.contains(i)) {
                System.out.printf("0x%02X ", i);
                if ((i - 1) % 16 == 15)
                    System.out.println();
            }
        }

        System.out.println("\nTotal implemented: " + implementedOpcodes.size());
        System.out.println("Total missing: " + (256 - implementedOpcodes.size()));
    }
}