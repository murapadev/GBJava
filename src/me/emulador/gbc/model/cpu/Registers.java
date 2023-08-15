package me.emulador.gbc.model.cpu;

public class Registers {
    private byte A, F, B, C, D, E, H, L;
    private short SP, PC;

    public Registers() {
        A = 0;
        F = 0;
        B = 0;
        C = 0;
        D = 0;
        E = 0;
        H = 0;
        L = 0;
        SP = 0;
        PC = 0;
    }

    public byte getA() {
        return A;
    }

    public void setA(byte a) {
        A = a;
    }

    public byte getF() {
        return F;
    }

    public void setF(byte f) {
        F = f;
    }

    public byte getB() {
        return B;
    }

    public void setB(byte b) {
        B = b;
    }

    public byte getC() {
        return C;
    }

    public void setC(byte c) {
        C = c;
    }

    public byte getD() {
        return D;
    }

    public void setD(byte d) {
        D = d;
    }

    public byte getE() {
        return E;
    }

    public void setE(byte e) {
        E = e;
    }

    public byte getH() {
        return H;
    }

    public void setH(byte h) {
        H = h;
    }

    public byte getL() {
        return L;
    }

    public void setL(byte l) {
        L = l;
    }

    public short getAF() {
        return (short) ((A << 8) | F);
    }

    public void setAF(short af) {
        A = (byte) (af >> 8);
        F = (byte) (af & 0xFF);
    }

    public short getBC() {
        return (short) ((B << 8) | C);
    }

    public void setBC(short bc) {
        B = (byte) (bc >> 8);
        C = (byte) (bc & 0xFF);
    }

    public short getDE() {
        return (short) ((D << 8) | E);
    }

    public void setDE(short de) {
        D = (byte) (de >> 8);
        E = (byte) (de & 0xFF);
    }

    public short getHL() {
        return (short) ((H << 8) | L);
    }

    public void setHL(short hl) {
        H = (byte) (hl >> 8);
        L = (byte) (hl & 0xFF);
    }

    public short getSP() {
        return SP;
    }

    public void setSP(short sp) {
        SP = sp;
    }

    public short getPC() {
        return PC;
    }

    public void setPC(short pc) {
        PC = pc;
    }
}