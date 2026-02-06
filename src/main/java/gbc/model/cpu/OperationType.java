package gbc.model.cpu;

/**
 * Categories for CPU operations.
 * Used for profiling, debugging, and instruction classification.
 */
public enum OperationType {
    MISC,
    CONTROL_FLOW,
    LOAD_TRANSFER,
    ARITHMETIC,
    LOGIC,
    INC_DEC,
    STACK_IO,
    BIT_MANIPULATION,
    /** Rotate and shift operations (RLCA, RRC, SLA, SRL, SWAP, etc.) */
    ROTATE_SHIFT
}
