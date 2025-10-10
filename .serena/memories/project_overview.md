# GBJava Project Overview

## Project Purpose
GBJava is a Work-In-Progress (WIP) Game Boy Color emulator written from scratch in Java. It aims to accurately emulate the Nintendo Game Boy and Game Boy Color hardware, including CPU, PPU, APU, memory management, and various peripherals.

## Tech Stack
- **Language**: Java 21
- **Build System**: Maven (multi-module project)
- **Testing**: JUnit 5 for unit tests, Mooneye test suite for hardware accuracy validation
- **UI**: Swing-based desktop application
- **Modules**:
  - `core`: Emulator components (CPU, PPU, APU, MMU, runtime utilities)
  - `shell`: Command-line entry points and manual harnesses
  - `ui`: Swing-based user interface widgets and controllers

## Key Components
- **CPU**: LR35902 processor emulation with accurate instruction timing
- **PPU**: Pixel Processing Unit for graphics rendering (supports DMG and GBC modes)
- **APU**: Audio Processing Unit for sound generation
- **Memory**: Memory Management Unit with cartridge support, RAM banks, I/O registers
- **Cartridge**: MBC (Memory Bank Controller) implementations for different cartridge types
- **Peripherals**: Timer, interrupts, DMA, serial communication

## Testing Strategy
- **Unit Tests**: JUnit tests for individual components
- **Integration Tests**: Mooneye test suite (75 acceptance tests) for hardware accuracy
- **Manual Testing**: ROM samples and save files for user experience validation

## Current Status
- 52 out of 75 Mooneye tests failing (69% pass rate)
- Core emulation working but timing and interrupt handling need fixes
- UI and CLI interfaces functional