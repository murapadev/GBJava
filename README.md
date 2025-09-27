# gbc

A WIP GB emulator made from scratch in Java.

## Project layout

The repository is an aggregated Maven build split across three main modules.
See `docs/PROJECT_STRUCTURE.md` for a full, sorted directory tree and
descriptions of the supporting assets.

- `core/` – Emulator CPU, PPU, APU, MMU and runtime utilities (`gbc.core.*`).
- `shell/` – Command-line entry points and manual harnesses (`gbc.cli.*`).
- `ui/` – Swing-based user interface widgets and controllers (`gbc.ui.*`).
- `mooneye-test-suite/` – Upstream compatibility ROMs used for validation.
- `samples/` – Local ROMs (`roms/`) and save files (`saves/`) used manually.
- `logs/` – Runtime logging artifacts (gitignored, safe to purge).

## Desktop UI

The Swing-based desktop shell includes a streamlined workflow for development and
debugging:

- A primary toolbar with one-click access to opening ROMs, pausing/resuming,
  stepping through instructions, resetting, capturing screenshots, and toggling
  fullscreen.
- Live display controls (scale, colour filters, scanlines, smoothing, aspect
  lock) that stay in sync with the menu options.
- A richer status strip that highlights the currently loaded ROM title, runtime
  state, active speed multiplier, and instantaneous FPS.
- Integrated shortcuts to the advanced debug and VRAM viewers, which now stay in
  lockstep with the main emulator controller.
