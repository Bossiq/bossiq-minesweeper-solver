# Minesweeper + Solver

A polished Minesweeper game built with **Java** and **JavaFX**, featuring a **three-pass deterministic solver** with probability-based guess hints, a modern dark theme, and cross-platform support.

**Made by [Oboroceanu Marian (@Bossiq)](https://github.com/Bossiq)** · Maastricht, NL

---

## ✨ Features

| Category | Details |
|----------|---------|
| ⛳ **First-click safety** | Mines placed after your first reveal — you never lose on click one |
| 🧠 **3-pass deterministic solver** | Single-cell constraints → subset/overlap analysis → endgame mine counting |
| 🎯 **Probability guess hints** | When stuck, highlights the safest cell with a pulsing animation and % probability |
| 👆 **Chording** | Double-click or middle-click a number to auto-reveal when flags match |
| 🚩 **Flag & question marks** | Right-click cycles: hidden → 🚩 → ❓ → hidden, with distinct red-tinted flag styling |
| 🌙 **Dark theme** | Modern glassmorphism-inspired UI with animations and hover effects |
| 🏆 **Win/loss overlay** | Animated overlay with stats, completion %, and play-again button |
| 🏅 **Best time tracking** | Persisted between sessions per difficulty |
| 📊 **Completion percentage** | Live progress tracking in the stats bar |
| 🔧 **Custom board sizes** | Beyond the 3 presets — define any width × height × mines |
| 📐 **Adaptive tile sizing** | Tiles scale down automatically on larger boards |
| 💾 **Save / Load** | Binary `.msw` format with integrity validation |
| 🎲 **Seeded games** | Enter a seed number to get the same board again |
| 📸 **Screenshot** | F8 saves the grid as PNG |
| ℹ️ **About / Help** | Built-in controls reference and info dialog |
| 🧪 **19 unit tests** | JUnit 5, covering model + solver |
| ⚡ **Performance optimized** | No GPU-expensive dropshadows on tiles, optimized O(n) solver, thread-safe timer |
| 📦 **Cross-platform** | Runs on Windows, macOS, and Linux |

---

## 🎮 Controls

| Action | Input |
|--------|-------|
| Reveal | Left click |
| Flag / Question / Clear | Right click (cycles) |
| Chord | Double-click or middle-click on a number |
| Restart | `R` |
| Solver step | `S` |
| Auto-solve | `A` |
| Clear all flags | `C` |
| Save / Load | `F5` / `F9` |
| Screenshot | `F8` |

---

## 🚀 Quick Start

### Prerequisites
- **Java 17+** (JDK) — any distribution
- No other dependencies needed — Gradle wrapper and JavaFX are bundled

### Run

```bash
git clone https://github.com/Bossiq/bossiq-minesweeper-solver.git
cd bossiq-minesweeper-solver

# macOS / Linux
chmod +x gradlew
./gradlew run

# Windows
gradlew.bat run
```

> **Note:** Gradle may show the build stuck at ~85% — this is normal. The app window is already open. Close it to finish the build.

### Test

```bash
./gradlew test
```

### Build Portable Image

```bash
./gradlew jlink
# → build/image/bin/minesweeper
```

### Create Installer

```bash
./gradlew jpackage
# Windows → .msi  |  macOS → .dmg  |  Linux → .deb
```

---

## 🏗️ Architecture

```
src/main/java/dev/bossiq/minesweeper/
├── App.java              ← JavaFX bootstrap
├── model/
│   ├── Board.java        ← Core game logic, flood-fill, save/load
│   ├── Cell.java         ← Cell state machine
│   ├── CellState.java    ← HIDDEN / REVEALED / FLAGGED
│   ├── Coord.java        ← (x, y) record
│   └── GameStats.java    ← Statistics snapshot with completion %
├── solver/
│   └── Solver.java       ← 3-pass solver + probability guess hints
└── ui/
    └── BoardView.java    ← Dark-themed UI with all controls

src/main/resources/
└── style.css             ← Dark theme stylesheet

src/test/java/
├── BoardTest.java        ← 10 tests (model + save/load)
└── SolverTest.java       ← 9 tests (solver passes + probability)
```

### Solver Algorithm

1. **Pass 1 — Single-cell constraint**: For each revealed number, if all mines around it are flagged → reveal the rest. If remaining hidden cells equal remaining mines → flag all.
2. **Pass 2 — Subset/overlap analysis**: Compares constraint pairs between neighbouring numbered cells using a cell-indexed lookup (avoids O(n²)). If one set of unknowns is a strict subset of another, infers safe cells or mines from the difference.
3. **Pass 3 — Endgame mine counting**: Uses global mine count — if remaining mines = total hidden cells → flag all; if remaining mines = 0 → reveal all.
4. **Guess hint**: When stuck, estimates per-cell mine probability using local constraints and highlights the safest cell with a pulsing green animation.

### Performance

- **No GPU-expensive effects** on tile rendering — dropshadows removed from all tile classes
- **Thread-safe solver** — timer paused during solving, grid disabled to prevent concurrent board access
- **Optimized constraint comparison** — Pass 2 uses cell-to-constraint indexing instead of all-pairs O(n²)
- **Solver state re-use** — `autoSolve()` reuses the best-guess from the last `step()` call

---

## 🛠️ Tech Stack

| Technology | Version |
|------------|---------|
| Java | 17+ |
| JavaFX | 17.0.12 |
| Gradle | 8.x (via wrapper) |
| JUnit | 5.11 |
| jlink/jpackage | Cross-platform installers |

---

## 📄 License

MIT

## 👤 Author

**Oboroceanu Marian** · [@Bossiq](https://github.com/Bossiq) · Maastricht, Netherlands
