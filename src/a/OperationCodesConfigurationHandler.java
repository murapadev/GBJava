package a;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class OperationCodesConfigurationHandler extends BaseConfigurationHandler<HashMap<Byte, OperationCode>> {

	Stack<OperationCode> stack;
	private StringBuilder currentCode;
	private String currentQName;
	
	public OperationCodesConfigurationHandler(HashMap<Byte, OperationCode> entity) {
		super(entity);
		stack = new Stack<OperationCode>();
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
		currentQName = qName;
		switch (qName.toLowerCase()) {
			case "opcode":
				String attribute;
				OperationCode current = new OperationCode((byte) 0x00, null, null, null);
				this.currentCode = new StringBuilder();
				for (int i = 0; i < attributes.getLength(); i++) {
					attribute = attributes.getQName(i);
					switch (attribute.toLowerCase()) {
						case "code":
							current.setOpcode(Byte.parseByte(attributes.getValue(i).replace("0x",""),16));
							break;
						case "name":
							ReflectionUtils.<OperationCode, String>modifyAttribute(current, "name", attributes.getValue(i));
							break;
						case "operands":
							List<String> operands = new ArrayList<String>(Arrays.asList(attributes.getValue(i).split(",")));
							ReflectionUtils.<OperationCode, List<String>>modifyAttribute(current, "operands", operands);
							break;
					}
				}
				stack.push(current);
				break;
		}
	}
	
	@Override
	public void characters(char[] paramArrayOfChar, int paramInt1, int paramInt2) throws SAXException {
		super.characters(paramArrayOfChar, paramInt1, paramInt2);
		if (this.currentQName != null && this.currentQName.equals("opcode")) {
			this.currentCode.append(new String(paramArrayOfChar,paramInt1,paramInt2));
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		super.endElement(uri, localName, qName);

		if (qName.toLowerCase().equals("opcode")) {
			OperationCode current = stack.pop();
			ReflectionUtils.<OperationCode, String>modifyAttribute(current, "code", this.currentCode.toString());
			System.out.println(current);
			this.returnObject.put((Byte)current.getOpcode(), current);
			
			current = null;
			this.currentCode = null;
		}
		this.currentQName = null;
	}
	
}
