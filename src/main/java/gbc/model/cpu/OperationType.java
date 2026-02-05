package gbc.model.cpu;

public enum OperationType {
    // TODO: Revisit categories once opcode tables are finalized (esp. LD/stack overlap).
    MISC,
    CONTROL_FLOW,
    LOAD_TRANSFER,
    ARITHMETIC,
    LOGIC,
    INC_DEC,
    STACK_IO,
    BIT_MANIPULATION
}
