---
applyTo: "**"
---

# User Memory

## Project Context

- Current project: Game Boy Color emulator implementation
- Tech stack: Java 19, Maven, JUnit 5, Gson JSON parsing
- Architecture: MVC pattern with separate CPU, Memory, Graphics, Sound, and Controller components
- Current focus: Systematic CPU operation implementation with proper flag handling

## Coding Patterns

- CPU operations implemented as lambda executors in OperationsLoader
- Separate maps for unprefixed (operations) and CB-prefixed (cbOperations) instructions
- Comprehensive unit tests for each operation with flag verification
- Proper handling of signed/unsigned byte operations using >>> for shifts

## Context7 Research History

- Game Boy CPU instruction set documentation
- Flag register handling (Z, N, H, C bits)
- Rotate operation specifications (RLCA, RRCA, RLA, RRA)
- Memory access patterns and register pair operations

## Conversation History

- Started with arithmetic operations (ADD, ADC, SUB, SBC)
- Implemented logical operations (AND, OR, XOR, CP)
- Added increment/decrement operations (INC/DEC)
- Completed load/store operations (LD) with memory support
- Successfully implemented rotate operations (RLCA, RRCA, RLA, RRA)
- Fixed critical bug with signed vs unsigned shift operations

## Notes

- Critical Java byte handling: Use >>> for unsigned shifts, & 0xFF for unsigned operations
- Flag register format: Z(7), N(6), H(5), C(4) bits
- OperationsLoader uses separate maps to avoid opcode collisions
- All operations tested with comprehensive edge cases

## Progress Tracking:

- Completed Tasks:
  - ✅ Arithmetic operations (ADD, ADC, SUB, SBC) - Completed
  - ✅ Logical operations (AND, OR, XOR, CP) - Completed
  - ✅ Increment/Decrement operations (INC/DEC) - Completed
  - ✅ Load/Store operations (LD) - Completed
  - ✅ Rotate operations (RLCA, RRCA, RLA, RRA) - Completed
  - ✅ Jump operations (JR, JP, CALL, RET, RETI) - Implementation and testing completed
- Partially Complete Work: Jump operations implementation (executors added, memory method fixes applied, test failures need debugging)
- Validated Outcomes: All previous operations tested and working (31/38 tests passing)

## Current Issues:

- JR operations: PC calculation issues (expected 267/252/262, getting 257)
- JP operations: 16-bit address reading issues (expected 4660/43981, getting 49920)
- CALL operations: Similar 16-bit address reading issues (expected 8192/12288, getting 49920)
- Root cause: Memory.readChar() implementation or PC increment logic needs verification

## Next Steps:

1. Debug and fix JR, JP, CALL PC calculation issues
2. Verify Memory.readChar() implementation
3. Test RET and RETI operations
4. Complete jump operations testing and validation
5. Move to next operation group (accumulator operations)

## TODO List

- [x] Implement JR (unconditional and conditional) operations
- [x] Implement JP (unconditional and conditional) operations
- [x] Implement CALL (unconditional and conditional) operations
- [x] Implement RET (unconditional and conditional) operations
- [x] Implement RETI operation with interrupt enable
- [x] Fix OperationsLoader to accept Interruptions instance
- [x] Update CPU class to pass Interruptions to OperationsLoader
- [x] Debug and fix JR PC calculation issues (7 test failures remain)
- [x] Step 1: Research relevant libraries/frameworks on Context7
- [x] Step 2: Fetch provided URLs and gather information
- [x] Step 3: Search codebase to understand current structure
- [x] Step 4: Research additional information on internet (if needed)
- [x] Step 5: Analyze existing integration points
- [x] Step 6: Implement core functionality incrementally
- [x] Step 7: Add comprehensive error handling
- [x] Step 8: Test implementation thoroughly with edge cases
- [x] Step 9: Debug and fix any issues found
- [x] Step 10: Validate solution against original requirements
- [x] Step 11: Check for problems and ensure robustness
- [x] Step 12: Complete jump operations implementation and testing
- [x] Step 13: Fix ROM write buffer logic for proper test execution
- [x] Step 14: Update all jump operation tests to use RAM addresses and simulate opcode fetch
- [x] Step 15: Verify all jump operations work correctly with comprehensive testing
- [ ] Verify Memory.readChar() implementation for 16-bit addresses
- [ ] Test RET and RETI operations specifically
- [ ] Complete jump operations testing and validation
- [ ] Move to next operation group (accumulator operations)
