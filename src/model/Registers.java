package model;

public class Registers {
    private byte A,B,C,D,E,F,H,L;
    private short SP,PC;

    public Registers(byte a, byte b, byte c, byte d, byte e, byte f, byte h, byte l, short SP, short PC) {
        A = a;
        B = b;
        C = c;
        D = d;
        E = e;
        F = f;
        H = h;
        L = l;
        this.SP = SP;
        this.PC = PC;
    }

    public Registers() {
        A = 0x01;
        B = 0x00;
        C = 0x13;
        D = 0x00;
        E = (byte)0xD8;
        F = (byte)0xB0;
        H = 0x01;
        L = 0x4d;
        this.SP = (short) 0xfffe;
        this.PC = 0x100;
    }

    public byte getA() {
        return A;
    }

    public void setA(byte a) {
        A = a;
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

    public byte getF() {
        return F;
    }

    public void setF(byte f) {
        F = f;
    }

    public byte getH() {
        return H;
    }

    public void setH(byte h) {
        H = h;
    }

    public short getAF(){
        return  (short) ((this.A<<8)|(this.F&0xFF));
    }

    public void setAF(short x){
        A = (byte) (x >> 8);
        F = (byte) x;
    }
    public short getBC(){
        return  (short) ((this.B<<8)|(this.C&0xFF));
    }

    public void setBC(short x){
        B = (byte) (x >> 8);
        C = (byte) x;
    }
    public short getDE(){
        return  (short) ((this.D<<8)|(this.E&0xFF));
    }

    public void setDE(short x){
        D = (byte) (x >> 8);
        E = (byte) x;
    }
    public short getHL(){
        return  (short) ((this.H<<8)|(this.L&0xFF));
    }

    public void setHL(short x){
        H = (byte) (x >> 8);
        L = (byte) x;
    }


    public byte getL() {
        return L;
    }

    public void setL(byte l) {
        L = l;
    }

    public short getSP() {
        return SP;
    }

    public void setSP(short SP) {
        this.SP = SP;
    }

    public short getPC() {
        return PC;
    }

    public void setPC(short PC) {
        this.PC = PC;
    }

}
