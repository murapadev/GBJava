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
		for (byte s : opcodes.keySet()) {
			System.out.println((short) s + ": " + opcodes.get(s).getName());
			System.out.println(opcodes.get(s).getCode());
		}
	} catch (SAXException | IOException | ParserConfigurationException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}

}
