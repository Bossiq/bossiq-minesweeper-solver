package dev.bossiq.minesweeper.model;

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
    @Override public String toString() {
        return "Stats{" +
                "w=" + width +
                ", h=" + height +
                ", mines=" + mines +
                ", safe=" + revealedSafeCells + "/" + totalSafeCells +
                ", flags=" + flagsUsed +
                ", moves=" + moves +
                ", over=" + gameOver +
                ", won=" + won +
                ", t=" + String.format("%.2f s", elapsedSeconds) +
                '}';
    }
}
