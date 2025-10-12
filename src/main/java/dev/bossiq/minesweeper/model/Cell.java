package dev.bossiq.minesweeper.model;

public final class Cell {
    private boolean mine;
    private int adjacentMines;
    private CellState state;

    public Cell() {
        this.mine = false;
        this.adjacentMines = 0;
        this.state = CellState.HIDDEN;
    }

    public boolean isMine() { return mine; }
    public void setMine(boolean mine) { this.mine = mine; }

    public int getAdjacentMines() { return adjacentMines; }
    public void setAdjacentMines(int count) {
        if (count < 0 || count > 8) throw new IllegalArgumentException("adjacentMines must be 0..8");
        this.adjacentMines = count;
    }

    public CellState getState() { return state; }
    public boolean isRevealed() { return state == CellState.REVEALED; }
    public boolean isFlagged() { return state == CellState.FLAGGED; }
    public boolean isHidden()  { return state == CellState.HIDDEN; }

    public void reveal() {
        if (state == CellState.FLAGGED) return;
        state = CellState.REVEALED;
    }

    public void toggleFlag() {
        if (state == CellState.REVEALED) return;
        state = (state == CellState.FLAGGED) ? CellState.HIDDEN : CellState.FLAGGED;
    }

    void forceSetState(CellState s) {
        this.state = s;
    }

}

