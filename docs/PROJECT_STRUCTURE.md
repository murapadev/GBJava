# Project structure

The repository is organised as a multi-module Maven build. The tree below groups the
folders by responsibility and keeps related modules together. Generated build
artifacts (the `target/` directories inside each module) are omitted for brevity.

```
GBJava/
├── README.md
├── emulator.properties          # Global emulator runtime defaults
├── pom.xml                      # Maven aggregator for all modules
├── GBemu.iml                    # IntelliJ IDEA project metadata
├── core/                        # Emulator core (CPU, PPU, APU, memory, runtime)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/gbc/        # Production sources
│       │   └── resources/       # JVM resources (instruction tables, etc.)
│       └── test/java/gbc/       # Unit and property tests for the core
├── shell/                       # Command-line utilities and manual harnesses
│   ├── pom.xml
│   └── src/
│       ├── main/java/gbc/       # CLI entry points
│       └── test/java/gbc/       # CLI-focused tests
├── ui/                          # Swing-based desktop user interface
│   ├── pom.xml
│   └── src/
│       ├── main/java/gbc/       # UI controllers, views, widgets
│       └── test/java/gbc/       # UI-level tests and fixtures
├── samples/
│   ├── roms/                    # Local ROMs used when experimenting
│   └── saves/                   # Emulator save files (ignored by git)
├── mooneye-test-suite/          # Upstream GB compatibility ROM test packs
│   ├── acceptance/              # High-level functional acceptance tests
│   ├── emulator-only/           # Tests requiring emulator features beyond DMG
│   ├── madness/                 # Edge-case PPU and DMA stress tests
│   ├── manual-only/             # Suites that need human validation
│   ├── misc/                    # Miscellaneous small ROM checks
│   └── utils/                   # Helper ROMs (boot dumper, etc.)
├── logs/                        # Runtime logs (gitignored)
└── docs/
    └── PROJECT_STRUCTURE.md     # This document
```

## Module overview

- **core** – Emulator components shared across every frontend. This is where the
  CPU, APU, PPU, MMU, scheduler and cross-cutting runtime utilities live.
- **shell** – Lightweight command-line frontends that embed the core to run ROMs
  or custom harnesses without a UI.
- **ui** – The Swing desktop application, including its controllers, rendering
  panels and debug tooling.

## Supporting assets

- **samples** – Personal ROM and save files used for manual testing.
- **mooneye-test-suite** – A curated copy of the well-known Game Boy compliance
  ROM suite, organised by category. These assets are invaluable when validating
  timing, interrupts, DMA and PPU behaviour.
- **logs** – Output traces produced while running the emulator. They stay out of
  version control so the directory can be safely wiped when needed.

## Build output

Each Maven module produces its own `target/` directory. Because these folders are
regenerated on every build, they are not shown above and can be removed without
side effects.
