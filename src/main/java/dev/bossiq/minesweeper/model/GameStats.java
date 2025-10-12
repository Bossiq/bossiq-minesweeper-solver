package dev.bossiq.minesweeper.model;

public final class GameStats {
    public final int width;
    public final int height;
    public final int mines;
    public final int totalSafeCells;
    public final int revealedSafeCells;
    public final int flagsUsed;
    public final int moves;
    public final boolean gameOver;
    public final boolean won;
    public final double elapsedSeconds;

    public GameStats(int width, int height, int mines,
                     int totalSafeCells, int revealedSafeCells,
                     int flagsUsed, int moves,
                     boolean gameOver, boolean won,
                     double elapsedSeconds) {
        this.width = width;
        this.height = height;
        this.mines = mines;
        this.totalSafeCells = totalSafeCells;
        this.revealedSafeCells = revealedSafeCells;
        this.flagsUsed = flagsUsed;
        this.moves = moves;
        this.gameOver = gameOver;
        this.won = won;
        this.elapsedSeconds = elapsedSeconds;
    }

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
