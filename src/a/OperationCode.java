package a;

import java.util.List;

public class OperationCode extends LoggeableEntity {

	private byte opcode;
	private final String name;
	private final List<String> operands;
	private String code;
	
	public OperationCode(byte opcode, String name, List<String> operands, String code) {
		super();
		this.opcode = opcode;
		this.name = name;
		this.operands = operands;
		this.code = code;
	}

	public byte getOpcode() {
		return opcode;
	}
	
	public void setOpcode(byte opcode) {
		this.opcode = opcode;
	}

	public String getName() {
		return name;
	}

	public List<String> getOperands() {
		return operands;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

}
