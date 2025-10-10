# GBJava Codebase Structure

## Core Architecture

### CPU (gbc.model.cpu)
- **CPU.java**: Main CPU emulation class with instruction execution loop
- **Registers.java**: CPU register file (A, F, B, C, D, E, H, L, PC, SP)
- **OperationsLoader.java**: Instruction decoder and executor
- **Interruptions.java**: Interrupt handling logic

### Memory (gbc.model.memory)
- **Memory.java**: Memory management unit with I/O register handling
- **Cartridge.java**: Cartridge and MBC (Memory Bank Controller) support
- Timer, PPU, and peripheral state management

### Graphics (gbc.model.graphics)
- **PPU.java**: Pixel Processing Unit for graphics rendering
- **PixelFifo.java**: Pixel FIFO implementation for DMG/GBC modes
- **Fetcher.java**: Background/window tile fetching
- **OamSearch.java**: Sprite search logic

### Sound (gbc.model.sound)
- **Apu.java**: Audio Processing Unit
- Individual channel implementations (Square, Wave, Noise)

## Test Structure

### Unit Tests (core/src/test/java)
- **CPUInstructionTest.java**: Individual CPU instruction testing
- **TimerBehaviorTest.java**: Timer overflow and reload behavior
- **DmgPixelFifoTest.java**: Pixel FIFO functionality

### Integration Tests
- **MooneyeAcceptanceTest.java**: Mooneye test suite runner
- Validates hardware accuracy against known-good ROMs

## Key Design Patterns

### Component-Based Architecture
- Each hardware component (CPU, PPU, APU, Memory) is a separate class
- Components communicate through well-defined interfaces
- Memory acts as central hub for I/O operations

### Cycle-Accurate Emulation
- All operations counted in T-cycles (4.194304 MHz base clock)
- PPU advances every T-cycle for accurate timing
- Timer and interrupts triggered at precise cycles

### State Machine Pattern
- PPU uses mode state machine (OAM, VRAM, HBlank, VBlank)
- Timer uses overflow/reload state machine
- CPU uses instruction execution state machine

### Observer Pattern
- Memory notifies CPU of interrupts
- PPU notifies memory of mode changes
- UI observes emulator state changes

## Critical Timing Points

### CPU Execution Loop
```
while (running) {
    fetch instruction
    decode instruction
    execute instruction
    step peripherals (PPU, timer, etc.)
    handle interrupts
}
```

### PPU Timing
- Mode 2 (OAM): 80 cycles - search for sprites
- Mode 3 (VRAM): 172 cycles - transfer pixels to LCD
- Mode 0 (HBlank): 204 cycles - horizontal blanking
- Mode 1 (VBlank): 456 cycles × 10 lines - vertical blanking

### Interrupt Timing
- Interrupts check after instruction completion
- EI takes effect after next instruction
- HALT resumes on interrupt or button press

## Memory Map
- 0x0000-0x3FFF: Cartridge ROM bank 0
- 0x4000-0x7FFF: Cartridge ROM bank N
- 0x8000-0x9FFF: Video RAM
- 0xA000-0xBFFF: Cartridge RAM
- 0xC000-0xDFFF: Work RAM
- 0xE000-0xFDFF: Echo RAM
- 0xFE00-0xFE9F: OAM (Object Attribute Memory)
- 0xFF00-0xFF7F: I/O Registers
- 0xFF80-0xFFFE: High RAM
- 0xFFFF: Interrupt Enable Register