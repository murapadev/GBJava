# Task Completion Guidelines for GBJava

## After Making Code Changes

### 1. Run Unit Tests
```bash
mvn test -Dtest="!MooneyeAcceptanceTest"
```
- Ensure all existing unit tests pass
- Fix any compilation errors
- Address any new test failures

### 2. Run Relevant Mooneye Tests
```bash
# Run specific test being worked on
mvn test -Dtest=MooneyeAcceptanceTest -Dmooneye.only="test_name"

# Or run a category of tests
mvn test -Dtest=MooneyeAcceptanceTest -Dmooneye.only="*timing*"
```

### 3. Validate Hardware Accuracy
- Check that register values match expected hardware behavior
- Verify timing is cycle-accurate
- Ensure interrupts fire at correct times
- Confirm PPU modes transition properly

### 4. Performance Check
- Run tests with timing to ensure no performance regressions
- Check for memory leaks or excessive object creation
- Verify emulator runs at expected speed

### 5. Code Quality
- Follow established code style and conventions
- Add appropriate documentation for new code
- Remove debug logging before committing
- Ensure code is readable and maintainable

## Debugging Mooneye Test Failures

### Common Failure Patterns
- **B=42**: Test detected emulator issue and entered error handler
- **Timeout**: Test runs for 50M cycles without reaching pass state
- **Wrong register values**: CPU registers don't match expected final state

### Debug Steps
1. **Enable tracing**: Add `-Dgbc.cpu.trace=true -Dgbc.cpu.trace.limit=1000`
2. **Check LCD state**: Add `-Dgbc.lcd.debug=true` for PPU issues
3. **Verify timing**: Check cycle counts and instruction timing
4. **Inspect memory**: Look at I/O register values during execution
5. **Compare with working tests**: Run similar passing tests for comparison

### Common Fix Categories
- **Boot state**: Fix initial register/memory values after reset
- **Instruction timing**: Correct cycle counts for CPU instructions
- **PPU timing**: Fix LCD mode transitions and STAT interrupts
- **Interrupt handling**: Correct EI/DI/HALT behavior
- **Timer behavior**: Fix overflow and reload timing
- **DMA operations**: Ensure proper transfer timing and restrictions

## Commit Guidelines
- **Atomic commits**: Each commit should fix one specific issue
- **Descriptive messages**: Explain what was fixed and why
- **Test results**: Include before/after test counts in commit message
- **Reference Mooneye tests**: Mention which tests now pass

## Validation Checklist
- [ ] Unit tests pass
- [ ] Mooneye tests run without crashes
- [ ] No performance regressions
- [ ] Code follows style guidelines
- [ ] Documentation updated if needed
- [ ] Debug logging removed
- [ ] Commit message is clear and descriptive