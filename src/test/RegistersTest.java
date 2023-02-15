package test;

import model.Registers;
import org.junit.jupiter.api.*;

class RegistersTest {

    @Test
    @DisplayName("Testing A register")
    void getA() {
        Registers a = new Registers();
        byte test = (byte)0xFF;
        a.setA(test);
        assert a.getA() == test;
    }

    @Test
    @DisplayName ("Testing 16 bits register AF")
    void setAF() {
        Registers a = new Registers();
        short test = (short)0xFFFF;
        a.setAF(test);
        assert a.getAF() == test;
        assert a.getA() == (byte)0xFF;
    }

    @Test
    @DisplayName("Testing 16 bits register HL")
    void setHL() {
        Registers a = new Registers();
        short test = (short)0x88FF;
        a.setHL(test);
        assert a.getHL() == test;
        assert a.getH() == (byte)0x88;
    }
}