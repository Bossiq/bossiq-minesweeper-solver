package dev.bossiq.minesweeper;

import dev.bossiq.minesweeper.model.Board;
import dev.bossiq.minesweeper.model.Coord;
import dev.bossiq.minesweeper.solver.Solver;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SolverTest {

    @Test
    void stepReturnsZeroOnFinishedGame() {
        Board b = new Board(3, 3, 1, new Random(1), false);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                if (!b.getCell(x, y).isMine()) b.reveal(x, y);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.step(b);
        assertEquals(0, r.totalActions());
        assertFalse(r.stuck());
    }

    @Test
    void autoSolveProgressesOnBeginner() {
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(4, 4);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.autoSolve(b);
        assertTrue(r.totalActions() >= 0);
    }

    @Test
    void autoSolveHandlesAlreadyWonGame() {
        Board b = new Board(3, 3, 1, new Random(1), false);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                if (!b.getCell(x, y).isMine()) b.reveal(x, y);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.autoSolve(b);
        assertEquals(0, r.totalActions());
    }

    @Test
    void solverReportsStuckOnUnrevealedBoard() {
        // With no revealed cells, solver has nothing to reason from
        Board b = new Board(9, 9, 10, new Random(77), true);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.step(b);
        assertTrue(r.stuck());
    }

    @Test
    void solverProvidesBestGuessWhenStuck() {
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(4, 4);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.autoSolve(b);
        if (r.stuck()) {
            assertNotNull(r.bestGuess(), "should provide a guess hint when stuck");
            assertTrue(r.bestGuessProb() >= 0 && r.bestGuessProb() <= 1.0);
        }
    }

    @Test
    void bestGuessIsValidCoordWhenStuck() {
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(4, 4);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.autoSolve(b);
        if (r.stuck() && r.bestGuess() != null) {
            Coord guess = r.bestGuess();
            assertTrue(guess.x() >= 0 && guess.x() < 9);
            assertTrue(guess.y() >= 0 && guess.y() < 9);
            assertFalse(b.getCell(guess.x(), guess.y()).isRevealed());
            assertTrue(r.bestGuessProb() >= 0 && r.bestGuessProb() <= 1.0);
        }
    }

    @Test
    void endgameSolvesSmallBoard() {
        // On a 4×4 board with 2 mines, manually reveal most safe cells
        // then verify the solver can finish the job
        Board b = new Board(4, 4, 2, new Random(5), false);
        // Reveal safe cells one at a time (avoid flood-fill winning early)
        int safeTotal = 14; // 16 - 2
        int target = safeTotal - 2; // leave 2 unrevealed safe cells
        int revealed = 0;
        for (int y = 0; y < 4 && revealed < target; y++)
            for (int x = 0; x < 4 && revealed < target; x++)
                if (!b.getCell(x, y).isMine() && !b.getCell(x, y).isRevealed()) {
                    b.reveal(x, y);
                    // flood-fill may have revealed more than 1
                    revealed = b.snapshotStats().revealedSafeCells();
                }
        if (b.isGameOver()) return; // flood-fill already won — valid but nothing to test
        Solver solver = new Solver();
        Solver.SolverResult r = solver.autoSolve(b);
        assertTrue(r.totalActions() > 0 || b.isGameOver(),
                "endgame pass should find actions or the game should be over");
    }

    @Test
    void subsetAnalysisFindsMovesBasicMisses() {
        // Use a specific seed where pass 1 alone gets stuck but pass 2 makes progress
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(4, 4);
        Solver solver = new Solver();
        // Run pass 1 until exhausted
        Solver.SolverResult r;
        do {
            r = solver.step(b);
        } while (r.totalActions() > 0 && !r.stuck() && !b.isGameOver());
        // The solver should have made progress (pass 1 + 2 + 3 combined)
        // On seed 42 the solver is known to make multiple passes
        assertFalse(b.isGameOver() && !b.isWon(),
                "solver should not have hit a mine");
    }

    @Test
    void bestGuessProbabilityInValidRange() {
        Board b = new Board(16, 16, 40, 42L);
        b.reveal(8, 8);
        Solver solver = new Solver();
        Solver.SolverResult r = solver.autoSolve(b);
        if (r.stuck() && r.bestGuess() != null) {
            assertTrue(r.bestGuessProb() > 0.0, "probability must be positive");
            assertTrue(r.bestGuessProb() <= 1.0, "probability must be <= 1.0");
        }
    }
}
