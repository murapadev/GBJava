package a;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class OperationCodesParseTest {

	public static void main(String[] args) {

	String path = OperationCodesParseTest.class.getClassLoader().getResource("opcodes.xml").getPath();

	try {
		Map<Byte, OperationCode> opcodes = XMLLoader.load(new OperationCodesConfigurationHandler(new HashMap()), path);

		for (byte b : opcodes.keySet()) {
			System.out.println(b + ": " + opcodes.get(b).getName());
		}
	} catch (SAXException | IOException | ParserConfigurationException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}

}
