package dev.bossiq.minesweeper.solver;

import dev.bossiq.minesweeper.model.Board;
import dev.bossiq.minesweeper.model.Cell;
import dev.bossiq.minesweeper.model.Coord;

import java.util.*;

/**
 * Deterministic Minesweeper solver with probability-based guess hints.
 * <p>
 * Three deterministic passes run in order:
 * <ol>
 *   <li><b>Single-cell constraint</b> – classic "all mines flagged → reveal rest"
 *       and "hidden-count equals remaining mines → flag all".</li>
 *   <li><b>Subset / overlap analysis</b> – compares constraints from neighbouring
 *       numbered cells; if one set of unknowns is a strict subset of another,
 *       the difference can be inferred.</li>
 *   <li><b>Endgame mine counting</b> – when global remaining-mines equals
 *       total hidden cells, flag all; when zero mines remain, reveal all.</li>
 * </ol>
 * If all three passes fail, the solver calculates per-cell mine probabilities
 * and reports the safest cell to click as a guess hint.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public final class Solver {

    /** Result of a solver invocation. */
    public record SolverResult(int flagsPlaced, int cellsRevealed, boolean stuck,
                               Coord bestGuess, double bestGuessProb) {
        public int totalActions() { return flagsPlaced + cellsRevealed; }

        /** Backward-compatible constructor (no guess info). */
        public SolverResult(int flagsPlaced, int cellsRevealed, boolean stuck) {
            this(flagsPlaced, cellsRevealed, stuck, null, 1.0);
        }
    }

    /** Internal constraint: a set of unknown cells that contain exactly {@code mines} mines. */
    private record Constraint(Set<Coord> cells, int mines) {}

    // ────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────

    /** Runs steps in a loop until no more progress can be made. */
    public SolverResult autoSolve(Board board) {
        int totalFlags = 0, totalRevealed = 0;
        SolverResult r;
        do {
            r = step(board);
            totalFlags    += r.flagsPlaced();
            totalRevealed += r.cellsRevealed();
        } while (r.totalActions() > 0 && !board.isGameOver());

        boolean stuck = r.totalActions() == 0 && !board.isGameOver();
        if (stuck) {
            // Reuse the bestGuess already computed by the last step() call
            return new SolverResult(totalFlags, totalRevealed, true,
                    r.bestGuess(), r.bestGuessProb());
        }
        return new SolverResult(totalFlags, totalRevealed, false);
    }

    /** One solver iteration: pass 1 → pass 2 → pass 3. */
    public SolverResult step(Board board) {
        if (board.isGameOver()) return new SolverResult(0, 0, false);

        Set<Coord> toFlag   = new LinkedHashSet<>();
        Set<Coord> toReveal = new LinkedHashSet<>();

        pass1(board, toFlag, toReveal);

        if (toFlag.isEmpty() && toReveal.isEmpty())
            pass2(board, toFlag, toReveal);

        if (toFlag.isEmpty() && toReveal.isEmpty())
            pass3(board, toFlag, toReveal);

        toReveal.removeAll(toFlag);

        // Apply actions
        int flagsPlaced = 0, cellsRevealed = 0;
        for (Coord c : toFlag) {
            Cell cell = board.getCell(c.x(), c.y());
            if (!cell.isRevealed() && !cell.isFlagged()) {
                board.toggleFlag(c.x(), c.y());
                flagsPlaced++;
                if (board.isGameOver())
                    return new SolverResult(flagsPlaced, cellsRevealed, false);
            }
        }
        for (Coord c : toReveal) {
            Cell cell = board.getCell(c.x(), c.y());
            if (!cell.isRevealed() && !cell.isFlagged()) {
                Board.RevealResult r = board.reveal(c.x(), c.y());
                cellsRevealed += r.revealed.size();
                if (board.isGameOver())
                    return new SolverResult(flagsPlaced, cellsRevealed, false);
            }
        }

        boolean stuck = flagsPlaced == 0 && cellsRevealed == 0;
        if (stuck) {
            Coord best = computeBestGuess(board);
            double prob = best != null ? estimateMineProbability(board, best) : 1.0;
            return new SolverResult(0, 0, true, best, prob);
        }
        return new SolverResult(flagsPlaced, cellsRevealed, false);
    }

    // ────────────────────────────────────────────────────────
    //  Pass 1 – classic single-cell constraint
    // ────────────────────────────────────────────────────────

    private void pass1(Board board, Set<Coord> toFlag, Set<Coord> toReveal) {
        int w = board.getWidth(), h = board.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Cell c = board.getCell(x, y);
                if (!c.isRevealed() || c.isMine()) continue;
                int number = c.getAdjacentMines();
                if (number == 0) continue;

                List<Coord> neighbors = neighbors(x, y, w, h);
                int flagged = 0, hidden = 0;
                List<Coord> hiddenCells = new ArrayList<>();
                for (Coord n : neighbors) {
                    Cell nc = board.getCell(n.x(), n.y());
                    if (nc.isFlagged()) flagged++;
                    else if (!nc.isRevealed()) { hidden++; hiddenCells.add(n); }
                }
                if (hidden == 0) continue;

                if (flagged == number)          toReveal.addAll(hiddenCells);
                if (flagged + hidden == number) toFlag.addAll(hiddenCells);
            }
        }
    }

    // ────────────────────────────────────────────────────────
    //  Pass 2 – subset / overlap analysis
    // ────────────────────────────────────────────────────────

    private void pass2(Board board, Set<Coord> toFlag, Set<Coord> toReveal) {
        int w = board.getWidth(), h = board.getHeight();
        List<Constraint> constraints = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Cell c = board.getCell(x, y);
                if (!c.isRevealed() || c.isMine()) continue;
                int number = c.getAdjacentMines();
                if (number == 0) continue;

                List<Coord> neighbors = neighbors(x, y, w, h);
                Set<Coord> unknowns = new LinkedHashSet<>();
                int flagged = 0;
                for (Coord n : neighbors) {
                    Cell nc = board.getCell(n.x(), n.y());
                    if (nc.isFlagged()) flagged++;
                    else if (!nc.isRevealed()) unknowns.add(n);
                }
                int remaining = number - flagged;
                if (!unknowns.isEmpty() && remaining >= 0)
                    constraints.add(new Constraint(unknowns, remaining));
            }
        }

        // Build cell → constraint index so we only compare constraints
        // that share at least one unknown cell (avoids O(n²) full scan)
        Map<Coord, List<Integer>> cellToIdx = new HashMap<>();
        for (int i = 0; i < constraints.size(); i++) {
            for (Coord c : constraints.get(i).cells) {
                cellToIdx.computeIfAbsent(c, k -> new ArrayList<>()).add(i);
            }
        }

        Set<Long> visited = new HashSet<>();
        for (List<Integer> group : cellToIdx.values()) {
            for (int gi = 0; gi < group.size(); gi++) {
                for (int gj = gi + 1; gj < group.size(); gj++) {
                    int i = group.get(gi), j = group.get(gj);
                    long key = ((long) Math.min(i, j) << 32) | Math.max(i, j);
                    if (!visited.add(key)) continue;
                    Constraint a = constraints.get(i), b = constraints.get(j);
                    analyseSubset(a, b, toFlag, toReveal);
                    analyseSubset(b, a, toFlag, toReveal);
                }
            }
        }
    }

    private void analyseSubset(Constraint sub, Constraint sup,
                               Set<Coord> toFlag, Set<Coord> toReveal) {
        if (sub.cells.size() >= sup.cells.size()) return;
        if (!sup.cells.containsAll(sub.cells)) return;

        Set<Coord> diff = new LinkedHashSet<>(sup.cells);
        diff.removeAll(sub.cells);
        int diffMines = sup.mines - sub.mines;

        if (diffMines == 0)              toReveal.addAll(diff);
        else if (diffMines == diff.size()) toFlag.addAll(diff);
    }

    // ────────────────────────────────────────────────────────
    //  Pass 3 – endgame global mine counting
    // ────────────────────────────────────────────────────────

    private void pass3(Board board, Set<Coord> toFlag, Set<Coord> toReveal) {
        int w = board.getWidth(), h = board.getHeight();
        List<Coord> hiddenCells = new ArrayList<>();

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                Cell c = board.getCell(x, y);
                if (!c.isRevealed() && !c.isFlagged())
                    hiddenCells.add(new Coord(x, y));
            }

        int minesLeft = board.getMinesRemaining();

        if (minesLeft == 0 && !hiddenCells.isEmpty())
            toReveal.addAll(hiddenCells);
        else if (minesLeft == hiddenCells.size() && !hiddenCells.isEmpty())
            toFlag.addAll(hiddenCells);
    }

    // ────────────────────────────────────────────────────────
    //  Probability-based guess hint
    // ────────────────────────────────────────────────────────

    /**
     * When the deterministic solver is stuck, estimates per-cell mine
     * probability using local constraints and returns the safest cell to click.
     */
    Coord computeBestGuess(Board board) {
        int w = board.getWidth(), h = board.getHeight();
        Map<Coord, Double> danger = new HashMap<>();

        // Collect all hidden, unflagged cells
        List<Coord> allHidden = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                Cell c = board.getCell(x, y);
                if (!c.isRevealed() && !c.isFlagged())
                    allHidden.add(new Coord(x, y));
            }
        if (allHidden.isEmpty()) return null;

        // Default probability for cells not constrained by any number
        int minesLeft = board.getMinesRemaining();
        double defaultProb = (double) minesLeft / allHidden.size();

        // For each revealed numbered cell, compute local mine probability
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Cell c = board.getCell(x, y);
                if (!c.isRevealed() || c.isMine()) continue;
                int number = c.getAdjacentMines();
                if (number == 0) continue;

                List<Coord> neighbors = neighbors(x, y, w, h);
                int flagged = 0;
                List<Coord> hiddenNeighbors = new ArrayList<>();
                for (Coord n : neighbors) {
                    Cell nc = board.getCell(n.x(), n.y());
                    if (nc.isFlagged()) flagged++;
                    else if (!nc.isRevealed()) hiddenNeighbors.add(n);
                }
                if (hiddenNeighbors.isEmpty()) continue;

                double localProb = (double) (number - flagged) / hiddenNeighbors.size();
                for (Coord n : hiddenNeighbors) {
                    // Take the maximum probability from all constraining cells
                    danger.merge(n, localProb, Math::max);
                }
            }
        }

        // For unconstrained cells, use the default probability
        for (Coord c : allHidden)
            danger.putIfAbsent(c, defaultProb);

        // Return the cell with the LOWEST mine probability
        return danger.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(allHidden.get(0));
    }

    /** Estimates the mine probability for a specific cell. */
    private double estimateMineProbability(Board board, Coord target) {
        int w = board.getWidth(), h = board.getHeight();
        double maxProb = 0;
        boolean constrained = false;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Cell c = board.getCell(x, y);
                if (!c.isRevealed() || c.isMine()) continue;
                int number = c.getAdjacentMines();
                if (number == 0) continue;

                List<Coord> neighbors = neighbors(x, y, w, h);
                int flagged = 0;
                List<Coord> hiddenNeighbors = new ArrayList<>();
                for (Coord n : neighbors) {
                    Cell nc = board.getCell(n.x(), n.y());
                    if (nc.isFlagged()) flagged++;
                    else if (!nc.isRevealed()) hiddenNeighbors.add(n);
                }
                if (!hiddenNeighbors.contains(target)) continue;

                constrained = true;
                double localProb = (double) (number - flagged) / hiddenNeighbors.size();
                maxProb = Math.max(maxProb, localProb);
            }
        }

        if (!constrained) {
            int totalHidden = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (!board.getCell(x, y).isRevealed() && !board.getCell(x, y).isFlagged())
                        totalHidden++;
            return totalHidden == 0 ? 1.0 : (double) board.getMinesRemaining() / totalHidden;
        }
        return maxProb;
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    private static List<Coord> neighbors(int x, int y, int w, int h) {
        List<Coord> res = new ArrayList<>(8);
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h)
                    res.add(new Coord(nx, ny));
            }
        return res;
    }
}
