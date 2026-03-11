package dev.bossiq.minesweeper.model;

/**
 * Immutable snapshot of game statistics at a moment in time.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public record GameStats(
        int width,
        int height,
        int mines,
        int totalSafeCells,
        int revealedSafeCells,
        int flagsUsed,
        int moves,
        boolean gameOver,
        boolean won,
        double elapsedSeconds
) {
    /** Percentage of safe cells that have been revealed (0–100). */
    public double completionPercent() {
        return totalSafeCells == 0 ? 0 : (revealedSafeCells * 100.0 / totalSafeCells);
    }

    @Override
    public String toString() {
        return "Stats{w=%d, h=%d, mines=%d, safe=%d/%d, flags=%d, moves=%d, over=%s, won=%s, t=%.2f s}"
                .formatted(width, height, mines, revealedSafeCells, totalSafeCells,
                           flagsUsed, moves, gameOver, won, elapsedSeconds);
    }
}
