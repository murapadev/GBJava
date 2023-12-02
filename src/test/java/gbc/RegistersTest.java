package gbc.model.cpu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegistersTest {
	private Registers registers;

	@BeforeEach
	public void setUp() {
		registers = new Registers();
	}

	@Test
	public void testGetAndSetRegisters() {
		byte[] testValues = {10, 20, 30, 40, 50, 60, 70, 80};
		String[] registerNames = {"A", "B", "C", "D", "E", "F", "H", "L"};

		for (int i = 0; i < registerNames.length; i++) {
			String register = registerNames[i];
			byte testValue = testValues[i];

			registers.setRegister(register, testValue);
			assertEquals(testValue, registers.getRegister(register), "Mismatch in register " + register);
		}
	}


	@Test
	public void testGetAndSetRegisterPairs() {
		registers.setAF((char) 100);
		assertEquals(100, registers.getAF());

		registers.setBC((char) 200);
		assertEquals(200, registers.getBC());

		registers.setDE((char) 300);
		assertEquals(300, registers.getDE());

		registers.setHL((char) 400);
		assertEquals(400, registers.getHL());

		registers.setSP((char) 500);
		assertEquals(500, registers.getSP());

		registers.setPC((char) 600);
		assertEquals(600, registers.getPC());
	}
}