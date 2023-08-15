package test;

import model.ExtendedInstructions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtendedInstructionsTest {

    @Test
    @DisplayName("Testing dissasembly")

    void dissasembly() {
        ExtendedInstructions cb = new ExtendedInstructions();
        assertEquals("RLC B", cb.dissasembly((byte) 0x00));
        assertEquals("SLA C", cb.dissasembly((byte) 0x21));
    }
}