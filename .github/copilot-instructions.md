# Game Boy Color Emulator - AI Coding Guidelines

## Project Overview

This is a Game Boy Color emulator written in Java 19, implementing a complete Game Boy CPU with accurate instruction execution, memory management, and graphics rendering. The project follows an MVC architecture with systematic CPU operation implementation.

## Architecture & Key Components

### Core Structure

- **MVC Pattern**: `GameBoyColor` (Model), `EmulatorController` (Controller), `EmulatorWindow` (View)
- **CPU Implementation**: Complete instruction set with lambda-based operation executors
- **Memory Management**: 64KB address space with cartridge loading and memory banking
- **Graphics**: PPU implementation with screen rendering and LCD controller

### Critical Files to Understand

- `src/main/java/main.java` - Application entry point
- `src/main/java/gbc/model/GameBoyColor.java` - Main emulator model
- `src/main/java/gbc/model/cpu/CPU.java` - CPU execution engine
- `src/main/java/gbc/model/cpu/OperationsLoader.java` - Instruction set implementation
- `src/main/java/gbc/model/memory/Memory.java` - Memory management system
- `src/main/resources/OperationCodes.json` - CPU instruction definitions

## Development Patterns & Conventions

### CPU Operations Implementation

```java
// Pattern: Lambda-based operation executors
if ("ADD".equals(operation.getMnemonic())) {
    return (registers, memory, operands) -> {
        // Implementation with proper flag handling
        byte result = (byte) ((destValue & 0xFF) + (srcValue & 0xFF));
        registers.setRegister("A", result);

        // Flag updates: Z, N, H, C
        byte flags = 0;
        if (result == 0) flags |= 0x80; // Z flag
        // ... other flags
        registers.setRegister("F", flags);
    };
}
```

### Critical Java Byte Handling

```java
// Essential patterns for Game Boy emulation
int unsignedValue = byteValue & 0xFF;           // Convert to unsigned
byte result = (byte) (value >>> 1);             // Unsigned right shift
int address = (high << 8) | (low & 0xFF);       // 16-bit address construction
```

### Flag Register Format

- **Z (bit 7)**: Zero flag - set when result is 0
- **N (bit 6)**: Subtract flag - set for subtraction operations
- **H (bit 5)**: Half-carry flag - set on carry from bit 3 to 4
- **C (bit 4)**: Carry flag - set on carry from bit 7

### Memory Access Patterns

```java
// Reading 16-bit values (little-endian)
public int readChar(int address) {
    int low = readByte(address);
    int high = readByte(address + 1);
    return (high << 8) | low;
}
```

## Testing Standards

### Unit Test Structure

```java
@Test
public void testADDOperation() {
    registers.setRegister("A", (byte) 0x10);
    registers.setRegister("B", (byte) 0x20);

    Operation addOp = operationsLoader.getOperation(0x80);
    addOp.getExecutor().execute(registers, memory, addOp.getOperands());

    // Verify result and ALL flags
    assertEquals(0x30, registers.getRegister("A") & 0xFF);
    assertEquals(0x00, flags & 0x80); // Z flag
    assertEquals(0x00, flags & 0x40); // N flag
    // ... complete flag verification
}
```

### Test Coverage Requirements

- **Flag Verification**: Every operation test must verify Z, N, H, C flags
- **Edge Cases**: Test zero results, carries, half-carries, overflows
- **Memory Operations**: Test both register and memory-based operations
- **Conditional Operations**: Test both condition met/not met scenarios

## Build & Development Workflow

### Maven Commands

```bash
mvn clean compile              # Compile the project
mvn test                       # Run all unit tests
mvn package                    # Create JAR file
mvn exec:java -Dexec.mainClass="main"  # Run the emulator
```

### Development Cycle

1. **Implement Operation**: Add lambda executor in `OperationsLoader.java`
2. **Write Tests**: Create comprehensive unit tests in `CPUTest.java`
3. **Verify Flags**: Ensure all flag conditions are correctly implemented
4. **Test Execution**: Run tests to validate against expected behavior
5. **Debug Issues**: Use memory dumps and register inspection for debugging

## Common Implementation Patterns

### Arithmetic Operations (ADD, ADC, SUB, SBC)

- Always use unsigned arithmetic: `(value & 0xFF)`
- Calculate half-carry: `((dest & 0x0F) + (src & 0x0F)) > 0x0F`
- Set N flag appropriately (0 for ADD/ADC, 1 for SUB/SBC)

### Logical Operations (AND, OR, XOR)

- Result affects only Z flag (set if result == 0)
- AND sets H flag to 1, others set H to 0
- C flag always cleared

### Load/Store Operations (LD)

- Handle immediate values: `memory.readByte(registers.getPC())`
- Support memory addressing: `memory.readByte(registers.getHL())`
- PC increment for immediate operands

### Jump Operations (JR, JP, CALL, RET)

- **JR**: Relative jumps with signed 8-bit offset
- **JP/CALL**: Absolute jumps with 16-bit addresses
- **Conditional**: Check flags before executing jump
- **Stack**: CALL pushes return address, RET pops it

## Debugging & Troubleshooting

### Memory Inspection

```java
// Check memory contents
System.out.println(memory.readByte(address) & 0xFF);
// Check register values
System.out.println(registers.getRegister("A") & 0xFF);
```

### Common Issues

- **Flag Calculation**: Double-check Z, H, C flag logic
- **PC Increment**: Ensure proper PC advancement for immediate operands
- **Stack Operations**: Verify SP updates and memory access
- **16-bit Operations**: Confirm little-endian byte ordering

## Current Development Focus

### Active Work Areas

- **Jump Operations**: JR, JP, CALL, RET implementations
- **Flag Handling**: Ensuring accurate flag state transitions
- **Memory Access**: Proper 16-bit address reading/writing
- **Stack Management**: PUSH/POP operations and SP handling

### Implementation Priority

1. Complete jump operation debugging
2. Implement remaining accumulator operations
3. Add interrupt handling
4. Integrate graphics and sound systems

## Code Quality Standards

### Naming Conventions

- **Methods**: camelCase with descriptive names
- **Variables**: descriptive names, avoid single letters except loop counters
- **Constants**: UPPER_CASE for opcode values and memory addresses

### Error Handling

- Use `IllegalArgumentException` for invalid addresses
- Validate operation parameters before execution
- Provide meaningful error messages for debugging

### Documentation

- Comment complex flag calculations
- Document operation-specific behavior
- Reference Game Boy documentation for accuracy

## Performance Considerations

### CPU Cycle Accuracy

- Each operation consumes specific CPU cycles
- Graphics and timer updates depend on cycle counting
- Maintain cycle accuracy for proper emulation timing

### Memory Access Optimization

- Minimize unnecessary memory reads/writes
- Cache frequently accessed memory regions
- Use efficient byte array operations

This codebase represents a systematic, test-driven approach to Game Boy emulation. Focus on maintaining the high standards of flag accuracy, comprehensive testing, and clean lambda-based operation implementation.
