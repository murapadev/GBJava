package gbc.controller;

/**
 * Immutable snapshot of CPU registers and execution flags for the debug UI.
 */
public record CpuSnapshot(
        int pc,
        int sp,
        int af,
        int bc,
        int de,
        int hl,
        int a,
        int b,
        int c,
        int d,
        int e,
        int f,
        int h,
        int l,
        boolean ime,
        boolean halted,
        boolean doubleSpeed
) {
}
