package dev.bossiq.minesweeper.model;

/**
 * Single cell on the Minesweeper board.
 * Tracks mine status, adjacent mine count, and display state.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public final class Cell {

    private boolean mine;
    private int adjacentMines;
    private CellState state;

    public Cell() {
        this.mine = false;
        this.adjacentMines = 0;
        this.state = CellState.HIDDEN;
    }

    // ── Mine ──

    public boolean isMine()            { return mine; }
    public void    setMine(boolean m)  { this.mine = m; }

    // ── Adjacent mine count ──

    public int getAdjacentMines() { return adjacentMines; }

    public void setAdjacentMines(int count) {
        if (count < 0 || count > 8)
            throw new IllegalArgumentException("adjacentMines must be 0..8, got " + count);
        this.adjacentMines = count;
    }

    // ── State ──

    public CellState getState() { return state; }
    public boolean isRevealed() { return state == CellState.REVEALED; }
    public boolean isFlagged()  { return state == CellState.FLAGGED; }
    public boolean isHidden()   { return state == CellState.HIDDEN; }

    public void reveal() {
        if (state == CellState.FLAGGED) return;
        state = CellState.REVEALED;
    }

    public void toggleFlag() {
        if (state == CellState.REVEALED) return;
        state = (state == CellState.FLAGGED) ? CellState.HIDDEN : CellState.FLAGGED;
    }

    /** Package-private: used by {@link Board#load} to restore saved state. */
    void forceSetState(CellState s) {
        this.state = s;
    }
}
