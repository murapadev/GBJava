# gbc

A WIP GB emulator made from scratch in Java.

## Project layout

- `core/`: emulator CPU, PPU, APU and memory models plus shared runtime utilities (`gbc.core.*`)
- `ui/`: Swing-based user interface widgets and controllers (`gbc.ui.*` packages)
- `shell/`: command-line entry points and manual harnesses (`gbc.cli.*` packages)
- `samples/roms/`: placeholder location for locally stored ROM files
- `samples/saves/`: placeholder location for save-state experiments
- `logs/`: runtime logging artifacts (ignored by git)
