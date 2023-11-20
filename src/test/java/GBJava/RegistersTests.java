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
	public void testSetAndGetRegister() {
		registers.setRegister("A", (byte) 10);
		assertEquals(10, registers.getRegister("A"));
	}

	@Test
	public void testSetAndGetRegisterPair() {
		registers.setRegisterPair("AF", (short) 300);
		assertEquals(300, registers.getRegisterPair("AF"));
	}

	@Test
	public void testSetAndGetSpecificRegisters() {
		registers.setA((byte) 10);
		assertEquals(10, registers.getA());

		registers.setB((byte) 20);
		assertEquals(20, registers.getB());

		registers.setC((byte) 30);
		assertEquals(30, registers.getC());

		registers.setD((byte) 40);
		assertEquals(40, registers.getD());

		registers.setE((byte) 50);
		assertEquals(50, registers.getE());

		registers.setF((byte) 60);
		assertEquals(60, registers.getF());

		registers.setH((byte) 70);
		assertEquals(70, registers.getH());

		registers.setL((byte) 80);
		assertEquals(80, registers.getL());
	}

	@Test
	public void testSetAndGetSpecificRegisterPairs() {
		registers.setAF((short) 100);
		assertEquals(100, registers.getAF());

		registers.setBC((short) 200);
		assertEquals(200, registers.getBC());

		registers.setDE((short) 300);
		assertEquals(300, registers.getDE());

		registers.setHL((short) 400);
		assertEquals(400, registers.getHL());

		registers.setSP((short) 500);
		assertEquals(500, registers.getSP());

		registers.setPC((short) 600);
		assertEquals(600, registers.getPC());
	}

	@Test
	public void testSetAndGetFlags() {
		registers.setFlags((byte) 10);
		assertEquals(10, registers.getFlags());
	}
}