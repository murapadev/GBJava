package me.emulador.gbc.model.cpu;

import java.util.HashMap;
import java.util.Map;

public class Registers {
    private Map<String, Byte> registers;

    public Registers() {
        registers = new HashMap<>();
        registers.put("A", (byte) 0);
        registers.put("B", (byte) 0);
        registers.put("C", (byte) 0);
        registers.put("D", (byte) 0);
        registers.put("E", (byte) 0);
        registers.put("F", (byte) 0);
        registers.put("H", (byte) 0);
        registers.put("L", (byte) 0);
        registers.put("P", (byte) 0);
        registers.put("C", (byte) 0);
        registers.put("S", (byte) 0);
        registers.put("P", (byte) 0);
    }

    public byte getRegister(String register) {
        if(register.length() != 1) throw new IllegalArgumentException("Register must be a single character");
        if(!registers.containsKey(register)) throw new IllegalArgumentException("Register must be one of A, B, C, D, E, F, H, L, P, C, S, P");
        return registers.get(register);
    }

    public short getRegisterPair(String register) {
        if(register.length() != 2) throw new IllegalArgumentException("Register must be two characters");
        if(!registers.containsKey(register.substring(0, 1)) || !registers.containsKey(register.substring(1, 2))) throw new IllegalArgumentException("Register must be one of AF, BC, DE, HL, SP, PC");
        return (short) ((this.getRegister(register.substring(0, 1)) << 8) + this.getRegister(register.substring(1, 2)));
    }

    public void setRegister(String register, byte value) {
        if(register.length() != 1) throw new IllegalArgumentException("Register must be a single character");
        if(!registers.containsKey(register)) throw new IllegalArgumentException("Register must be one of A, B, C, D, E, F, H, L, P, C, S, P");
        registers.put(register, value);
    }

    public void setRegisterPair(String register, short value) {
        if(register.length() != 2) throw new IllegalArgumentException("Register must be two characters");
        if(!registers.containsKey(register.substring(0, 1)) || !registers.containsKey(register.substring(1, 2))) throw new IllegalArgumentException("Register must be one of AF, BC, DE, HL, SP, PC");
        this.setRegister(register.substring(0, 1), (byte) ((value & 0xFF00) >> 8));
        this.setRegister(register.substring(1, 2), (byte) (value & 0x00FF));
    }

    public void setA(byte value) {
        registers.put("A", value);
    }

    public void setB(byte value) {
        registers.put("B", value);
    }

    public void setC(byte value) {
        registers.put("C", value);
    }

    public void setD(byte value) {
        registers.put("D", value);
    }

    public void setE(byte value) {
        registers.put("E", value);
    }

    public void setF(byte value) {
        registers.put("F", value);
    }

    public void setH(byte value) {
        registers.put("H", value);
    }

    public void setL(byte value) {
        registers.put("L", value);
    }

    public void setSP(short value) {
        this.setRegisterPair("SP", value);
    }

    public void setPC(short value) {
        this.setRegisterPair("PC", value);
    }

    public void setAF(short value) {
        this.setRegisterPair("AF", value);
    }

    public void setBC(short value) {
        this.setRegisterPair("BC", value);
    }

    public void setDE(short value) {
        this.setRegisterPair("DE", value);
    }

    public void setHL(short value) {
        this.setRegisterPair("HL", value);
    }

    public byte getA() {
        return registers.get("A");
    }

    public byte getB() {
        return registers.get("B");
    }

    public byte getC() {
        return registers.get("C");
    }

    public byte getD() {
        return registers.get("D");
    }

    public byte getE() {
        return registers.get("E");
    }

    public byte getF() {
        return registers.get("F");
    }

    public byte getH() {
        return registers.get("H");
    }

    public byte getL() {
        return registers.get("L");
    }

    public short getSP() {
        return this.getRegisterPair("SP");
    }

    public short getPC() {
        return this.getRegisterPair("PC");
    }

    public short getAF() {
        return this.getRegisterPair("AF");
    }

    public short getBC() {
        return this.getRegisterPair("BC");
    }

    public short getDE() {
        return this.getRegisterPair("DE");
    }

    public short getHL() {
        return this.getRegisterPair("HL");
    }

    public void setFlags(byte value) {
        this.setF(value);
    }

    public byte getFlags() {
        return this.getF();
    }
    

}

