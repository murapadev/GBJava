# Repository Guidelines

## Project Structure & Module Organization

The root `pom.xml` orchestrates four Maven modules. `api/src/main/java/gbc` defines shared contracts, `core/src/main/java/gbc` houses the emulation hardware, `ui/src/main/java/gbc` renders the Swing interface, and `shell/src/main/java` bootstraps the desktop launcher. Reference ROMs (`Tetris.gb`, `Pokemon.gb`) live at the repository root; prefer keeping experimental ROMs and saves out of version control.

## Build, Test, and Development Commands

Run `mvn clean install` at the root to compile every module and execute the default tests. During UI work, `mvn -pl shell -am package` rebuilds only the launcher and its dependencies. Launch the emulator after packaging with `java -cp "shell/target/classes:ui/target/classes:core/target/classes:api/target/classes" main ./Tetris.gb`; swap in any ROM path as needed. Continuous integration should call `mvn test -Pheadless-tests` to force headless graphics and mute audio.

## Coding Style & Naming Conventions

Target Java 21 and remain inside the `gbc.*` package tree per module. Follow the prevailing style: tabs for indentation, braces on the same line, PascalCase for classes, and camelCase members. Keep Swing wiring in `ui`, emulation state in `core`, and shared DTOs in `api` so the dependency chain stays acyclic. Rerun your IDE formatter or inspections before committing to minimize cosmetic churn.

## Testing Guidelines

Place tests under `module/src/test/java` mirroring the main package layout. Prefer JUnit 5 annotations and engine; fall back to JUnit 4 only for legacy fixtures. Name classes `<Subject>Test` and keep method names descriptive of the behavior under test. Reuse the `gbc.ppu.dotScale` system property (already configured in the POM) to accelerate PPU-heavy checks and document any timing assumptions in the assertion messages.

## Commit & Pull Request Guidelines

Commits should follow the observed pattern (`feat: descripción`, `fix: ...`, `update:`) with summaries under 72 characters and any context or TODOs in the body. Group related edits together and leave unrelated refactors for a separate commit. Pull requests need a short feature or bug summary, the commands or ROM used to validate the change, and UI evidence when visuals move. Link issues via `Closes #id` so automation stays in sync.

## ROM Assets & Configuration Tips

Track only redistributable ROMs; otherwise, ignore them locally. When sharing save files, mention the ROM hash they target and any JVM flags required to reproduce your run.
