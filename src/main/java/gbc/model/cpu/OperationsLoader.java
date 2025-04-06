package gbc.model.cpu;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gbc.model.memory.Memory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OperationsLoader {
    private Map<Integer, Operation> operations;
    private Map<String, OperationExecutor> executors;

    public OperationsLoader() {
        this.operations = new HashMap<>();
        this.executors = new HashMap<>();
        loadOperations();
    }

    private void loadOperations() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("OperationCodes.json");
             InputStreamReader isr = new InputStreamReader(is)) {
            Gson gson = new Gson();
            JsonObject rootObj = gson.fromJson(isr, JsonObject.class);

            JsonObject unprefixed = rootObj.getAsJsonObject("unprefixed");
            parseOperationSection(unprefixed, gson);

            JsonObject cbprefixed = rootObj.getAsJsonObject("cbprefixed");
            parseOperationSection(cbprefixed, gson);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseOperationSection(JsonObject operationSection, Gson gson) {
        for (Map.Entry<String, JsonElement> entry : operationSection.entrySet()) {
            try {
                // Remove the '0x' prefix if present and trim any whitespace
                String key = entry.getKey().replace("0x", "").trim();
                // Now parse the clean hexadecimal string
                int opcode = Integer.parseInt(key, 16);
                Operation operation = gson.fromJson(entry.getValue(), Operation.class);
                operation.setExecutor(createExecutorForOperation(operation));
                operations.put(opcode, operation);
            } catch (NumberFormatException e) {
                System.err.println("Invalid key format for entry: '" + entry.getKey() + "'");
            }
        }
    }

    private OperationExecutor createExecutorForOperation(Operation operation) {
        if ("RET".equals(operation.getMnemonic())) {
            return (registers, memory, operands) -> {
                // Lambda for RET executor
                System.out.println("Executing RET operation");
                // Typically, RET pops an address from the stack and sets the program counter to that address.
                // Example implementation (assuming these methods exist in your Registers and Memory classes):
                char address = memory.popFromStack();
                registers.setPC(address);
            };
        }
        if ("LD".equals(operation.getMnemonic())) {
            return (registers, memory, operands) -> {
                // Lambda for LD executor
                System.out.println("Executing LD operation");
                // LD typically loads a value into a register or memory location.
                // The exact behavior depends on the operands.
                // For example, if operands specify a register and a value:
                if (operands.size() >= 2) {
                    String register = operands.get(0).get("register").toString();
                    int value = Integer.parseInt(operands.get(1).get("value").toString(), 16);
                    registers.setRegister(register, (byte) value);
                }
            };
        }
        if ("JP".equals(operation.getMnemonic())) {
            return (registers, memory, operands) -> {
                // Lambda for JP executor
                System.out.println("Executing JP operation");
                // JP typically sets the program counter to a given address.
                // The exact behavior depends on the operands.
                // For example, if operands specify a value:
                if (operands.size() >= 1) {
                    int value = Integer.parseInt(operands.get(0).get("value").toString(), 16);
                    registers.setPC((char) value);
                }
            };
        }

        if("SET".equals(operation.getMnemonic())) {
        	return (registers, memory, operands) -> {
        		// Lambda for SET executor
        		System.out.println("Executing SET operation");
        		// SET typically sets a bit in a register or memory location.
        		// The exact behavior depends on the operands.
        		// For example, if operands specify a register and a bit:
        		if (operands.size() >= 2) {
        			String register = operands.get(0).get("name").toString();
        		}
        	};
        }

        if("RES".equals(operation.getMnemonic())) {
        	return (registers, memory, operands) -> {
        		// Lambda for RES executor
        		System.out.println("Executing RES operation");
        		// RES typically resets a bit in a register or memory location.
        		// The exact behavior depends on the operands.
        		// For example, if operands specify a register and a bit:
        		if (operands.size() >= 2) {
        			String register = operands.get(0).get("register").toString();
        			int bit = Integer.parseInt(operands.get(1).get("bit").toString(), 16);
        			registers.setRegister(register, (byte) (registers.getRegister(register) & ~(1 << bit)));
        		}
        	};
        }

        if("BIT".equals(operation.getMnemonic())) {
        	return (registers, memory, operands) -> {
        		// Lambda for BIT executor
        		System.out.println("Executing BIT operation");
        		// BIT typically checks a bit in a register or memory location.
        		// The exact behavior depends on the operands.
        		// For example, if operands specify a register and a bit:
        		if (operands.size() >= 2) {
        			String register = operands.get(0).get("register").toString();
        			int bit = Integer.parseInt(operands.get(1).get("bit").toString(), 16);
        			if((registers.getRegister(register) & (1 << bit)) == 0) {
        				registers.setRegister("F", (byte) (registers.getRegister("F") | 0x80));
        			}
        			else {
        				registers.setRegister("F", (byte) (registers.getRegister("F") & 0x7F));
        			}
        		}
        	};
        }



        // Other mnemonics...
        return (registers, memory, operands) -> {
            // Lambda for default executor
            System.out.println("Executing default operation for mnemonic: " + operation.getMnemonic());
        };
    }


    public Operation getOperation(int opcode) {
        return operations.get(opcode);
    }

    // Other methods...
}
