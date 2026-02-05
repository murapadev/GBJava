# Emulator Plan (DMG + CGB)

## Goals
- Build a **single core** with clean DMG/CGB profiles, minimizing scattered mode checks.
- Achieve **cycle‑accurate behavior** for CPU, PPU, timer, DMA, and interrupts.
- Reach **stable test coverage** (blargg, mooneye emulator‑only, acid2) without relying on real boot ROMs.
- Preserve correctness first, then optimize performance.

## Guiding Principles
- **Hardware profiles** (DMG, DMG0, MGB, SGB, CGB) drive initial state and IO quirks.
- **Sub‑systems own their timing**, coordinated by a dot‑accurate clock.
- **No silent fallbacks** for unimplemented opcodes/behaviors—log and test.
- **Deterministic startup** (no boot ROM) with explicit post‑boot state and header‑based presets.

---

## Phase 0 — Baseline & Instrumentation
**Objective:** Make behavior observable and reproducible.
- Add side‑effect‑free `peek` reads for debugger/disassembler.
- Centralize trace/log toggles and avoid IO side effects in debug tooling.
- Create minimal “core sanity” tests (CPU reset state, IO defaults, timer behavior).

**Deliverables:**
- `MemoryBus.peekByte/peekWord` (or similar).
- Trace and debugger paths use peek.
- Deterministic reset snapshots for each hardware profile.

---

## Phase 1 — CPU Core Accuracy
**Objective:** Instruction correctness + cycle accuracy.
- Validate opcode tables and cycle counts against authoritative sources.
- Remove fallback executor paths; implement all opcodes.
- Fix edge cases: HALT bug, STOP behavior, EI/DI delays, interrupt dispatch timing.
- Verify double‑speed behavior for CGB (timer/DMA/serial pacing).

**Deliverables:**
- Full opcode coverage with verified cycles.
- CPU timing tests (blargg `cpu_instrs`, `instr_timing`).

---

## Phase 2 — Timer & Interrupts
**Objective:** Accurate DIV/TIMA logic and interrupt timing.
- Validate TAC write behavior and edge‑triggered TIMA increments.
- Model overflow and reload delays exactly.
- Ensure IF/IE upper‑bit behavior matches hardware.

**Deliverables:**
- Mooneye timer tests and edge‑case unit tests.

---

## Phase 3 — Memory, DMA, and Bus Conflicts
**Objective:** Correct memory map and bus contention.
- Separate DMG/CGB IO behavior and masks.
- Model OAM/VRAM blocking per mode and dot.
- Refine OAM DMA startup/restart timing and CGB HDMA/HBlank rules.

**Deliverables:**
- Mooneye DMA tests passing; documented bus conflict behavior.

---

## Phase 4 — PPU (Tiles/Sprites/Window)
**Objective:** Dot‑accurate rendering and priority rules.
- Implement a dot‑accurate pipeline with explicit fetch states.
- Correct sprite fetch timing, FIFO draining, and sprite priority rules.
- Fix window trigger rules (WX/WY edge cases, mid‑line behavior).
- Separate DMG and CGB renderers where logic diverges.

**Deliverables:**
- `dmg-acid2` and `cgb-acid2` pass with stable hashes.
- PPU‑focused mooneye tests pass.

---

## Phase 5 — APU (Audio)
**Objective:** Hardware‑accurate audio timing and mixing.
- Enforce DAC rules and channel enable/disable side effects.
- Correct sweep/envelope timing and LFSR behavior.
- Validate wave RAM access restrictions and DMG/CGB differences.

**Deliverables:**
- `dmg_sound` and `cgb_sound` test suites pass (blargg).

---

## Phase 6 — Cartridge & RTC
**Objective:** Robust cartridge support.
- Validate header logo/checksum and surface warnings.
- Implement accurate MBC behavior (MBC1/2/3/5 edge cases).
- Persist RTC and battery‑backed RAM correctly.

**Deliverables:**
- Emulator‑only MBC tests and RTC coverage pass.

---

## Phase 7 — Performance & UX
**Objective:** Stable throughput with accurate timing.
- Optimize hot paths (PPU FIFO, memory reads).
- Remove unnecessary allocations in frame buffers.
- Add fast paths for common operations without breaking accuracy.

**Deliverables:**
- Stable 60 FPS at target speed on reference machine.

---

## Test Strategy
1. **Unit tests** for reset states and individual registers.
2. **Blargg** CPU/PPU/audio/timer suites.
3. **Mooneye emulator‑only** for memory/DMA/edge timing.
4. **Acid2** visual hashes for DMG/CGB.
5. **Regression** tests for known issues.

---

## Milestones
1. **Core CPU + Timer** accurate (blargg CPU + timer pass).
2. **PPU pipeline** stable (acid2 hash match).
3. **DMA/Bus** correct (mooneye DMA/IO pass).
4. **Audio** correct (blargg sound pass).
5. **Cartridge/RTC** complete (emulator‑only MBC tests pass).

---

## Non‑Goals (for now)
- Real boot ROM execution.
- Super Game Boy enhancements beyond basic compatibility.
- Full hardware‑level power/clock noise emulation.

