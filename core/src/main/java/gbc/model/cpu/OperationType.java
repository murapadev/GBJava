package gbc.model.cpu;

/**
 * High-level grouping for Game Boy CPU instructions. Keeping operations grouped by type
 * makes it easier to reason about coverage and to extend the emulator incrementally.
 */
public enum OperationType {
    LOAD_TRANSFER,
    ARITHMETIC,
    LOGIC,
    INC_DEC,
    BIT_MANIPULATION,
    CONTROL_FLOW,
    STACK_IO,
    MISC
}
