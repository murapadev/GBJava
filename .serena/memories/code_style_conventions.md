# Code Style and Conventions for GBJava

## Java Version and Features
- **Java Version**: 21 (set in pom.xml)
- **Language Features**: Modern Java features allowed (records, pattern matching, etc.)
- **Encoding**: UTF-8 for all source files

## Package Structure
- **Base Package**: `gbc` (Game Boy Color)
- **Core Components**: `gbc.model.*` (cpu, memory, graphics, cartridge, sound)
- **UI Components**: `gbc.ui.*` (controller, view)
- **CLI Components**: `gbc.cli.*`
- **Tests**: `gbc.*` (matching main package structure)

## Naming Conventions
- **Classes**: PascalCase (e.g., `CPU`, `Memory`, `PPU`)
- **Methods**: camelCase (e.g., `stepPeripherals()`, `readByte()`)
- **Variables**: camelCase (e.g., `modeClock`, `lcdcRegister`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `LCD_CYCLES_PER_LINE`)
- **Packages**: lowercase (e.g., `gbc.model.cpu`)

## Code Organization
- **Class Structure**: Fields first, then constructors, then public methods, then private methods
- **Method Ordering**: Group related functionality together
- **Import Style**: Organize imports by package (java.*, then third-party, then project)
- **Access Modifiers**: Use appropriate visibility (prefer private, then package-private, then protected, then public)

## Documentation
- **Class Comments**: Javadoc for public classes explaining purpose
- **Method Comments**: Javadoc for public methods with parameters and return values
- **Inline Comments**: Explain complex logic or hardware-specific behavior
- **TODO Comments**: Use for known issues or future improvements

## Error Handling
- **Exceptions**: Use appropriate exception types
- **Logging**: Use System.out.println() for debug output (simple approach)
- **Assertions**: Use JUnit assertions for tests

## Hardware Accuracy
- **Cycle Accuracy**: Instructions must execute in correct number of T-cycles
- **Register Accuracy**: All CPU registers and I/O registers must match hardware
- **Timing Accuracy**: PPU, timer, and interrupt timing must be cycle-accurate
- **Memory Accuracy**: Memory access timing and restrictions must be emulated

## Testing Conventions
- **Test Names**: descriptive names explaining what is being tested
- **Test Structure**: Arrange-Act-Assert pattern
- **Mooneye Tests**: Use the MooneyeAcceptanceTest framework for hardware validation
- **Debug Flags**: Use system properties for enabling debug output during tests

## Performance Considerations
- **Memory Efficiency**: Avoid unnecessary object creation in hot paths
- **Cycle Counting**: Accurate cycle counting is critical for timing-dependent behavior
- **Array Bounds**: Check array bounds in debug mode but optimize for production

## Hardware-Specific Patterns
- **Register Access**: Use bit manipulation for flag checking/setting
- **Memory Mapping**: Use address ranges for different memory regions
- **Interrupt Handling**: Proper IME (Interrupt Master Enable) flag management
- **DMA Operations**: Cycle-accurate DMA transfers with memory access restrictions