# gbc

A WIP GB emulator made from scratch in Java.

## Project layout

Single-module Maven project with source and tests under `src/`.

- `src/main/java/gbc/` – Emulator core, UI, and CLI entry point.
- `src/main/resources/` – Runtime resources (palette data, etc.).
- `src/test/java/gbc/` – Unit and acceptance tests.
- `samples/` – Local ROMs used for manual testing.
- `saves/` – Save files used manually.
- `logs/` – Runtime logging artifacts (safe to purge).

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

## Debugging (JDWP)

Run the emulator with the debug profile so a debugger can attach:

```bash
mvn -Pdebug -DskipTests compile exec:exec
```

Optional flags:

```bash
# Change the debug port (default 5005)
mvn -Pdebug -Ddebug.port=5006 -DskipTests compile exec:exec

# Wait for the debugger to attach before starting
mvn -Pdebug -Ddebug.suspend=y -DskipTests compile exec:exec
```

Attach your debugger to `localhost:5005` (or the port you chose).
