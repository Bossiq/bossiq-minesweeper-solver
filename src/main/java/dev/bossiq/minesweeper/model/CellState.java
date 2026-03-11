package dev.bossiq.minesweeper.model;

/**
 * Possible display states for a {@link Cell}.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public enum CellState {
    /** Not yet revealed or flagged. */
    HIDDEN,
    /** Revealed by the player or flood-fill. */
    REVEALED,
    /** Flagged as a suspected mine. */
    FLAGGED
}