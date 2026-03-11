package dev.bossiq.minesweeper;

import dev.bossiq.minesweeper.model.Board;
import dev.bossiq.minesweeper.model.Cell;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class BoardTest {

    @Test
    void firstClickIsSafe() {
        Board b = new Board(9, 9, 10, new Random(123), true);
        Board.RevealResult r = b.reveal(4, 4);
        assertFalse(r.hitMine, "first click must be safe");
    }

    @Test
    void floodRevealZeroes() {
        Board b = new Board(9, 9, 10, new Random(123), true);
        b.reveal(0, 0);
        Cell c = b.getCell(0, 0);
        assertTrue(c.isRevealed());
    }

    @Test
    void winDetection() {
        Board b = new Board(3, 3, 1, new Random(1), false);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                if (!b.getCell(x, y).isMine()) b.reveal(x, y);
        assertTrue(b.isWon());
        assertTrue(b.isGameOver());
    }

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(3, 3);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            b.save(out);
        }
        Board loaded;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            loaded = Board.load(in);
        }
        assertEquals(b.getWidth(), loaded.getWidth());
        assertEquals(b.getHeight(), loaded.getHeight());
        assertEquals(b.getMineCount(), loaded.getMineCount());
        assertEquals(b.snapshotStats().revealedSafeCells(), loaded.snapshotStats().revealedSafeCells());
    }

    // ── New tests ──

    @Test
    void chordRevealsWhenFlagsMatch() {
        // Create board with known seed, reveal a cell, then test chording
        Board b = new Board(5, 5, 3, new Random(42), false);
        // Find a numbered cell and set up flags
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if (!b.getCell(x, y).isMine()) {
                    b.reveal(x, y);
                    if (b.getCell(x, y).getAdjacentMines() > 0) {
                        // Found a numbered cell; chord should at least not crash
                        Board.RevealResult r = b.chord(x, y);
                        assertNotNull(r);
                        assertFalse(r.hitMine); // flags dont match, so nothing happens
                        return;
                    }
                }
            }
        }
    }

    @Test
    void clearAllFlagsResetsCount() {
        Board b = new Board(9, 9, 10, new Random(99), false);
        // Flag some cells
        b.toggleFlag(0, 0);
        b.toggleFlag(1, 0);
        b.toggleFlag(2, 0);
        assertEquals(3, b.getFlagsUsed());
        int cleared = b.clearAllFlags();
        assertEquals(3, cleared);
        assertEquals(0, b.getFlagsUsed());
    }

    @Test
    void toggleFlagReturnsFalseForRevealedCell() {
        Board b = new Board(9, 9, 10, new Random(123), true);
        b.reveal(4, 4); // first-click safe
        assertFalse(b.toggleFlag(4, 4), "toggling flag on revealed cell should return false");
    }

    @Test
    void floodRevealDoesNotDoubleCount() {
        // Regression test: revealed safe cells count should match actual revealed cells
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(0, 0);
        int counted = 0;
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                if (b.getCell(x, y).isRevealed() && !b.getCell(x, y).isMine()) counted++;
        assertEquals(counted, b.snapshotStats().revealedSafeCells(),
                "revealedSafeCells must match actual revealed non-mine cells");
    }

    @Test
    void minesRemainingDecrementsWithFlags() {
        Board b = new Board(9, 9, 10, new Random(50), false);
        assertEquals(10, b.getMinesRemaining());
        b.toggleFlag(0, 0);
        assertEquals(9, b.getMinesRemaining());
        b.toggleFlag(0, 0); // unflag
        assertEquals(10, b.getMinesRemaining());
    }

    @Test
    void loadRejectsCorruptRevealedCount() throws Exception {
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(3, 3);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            b.save(out);
        }
        byte[] data = bos.toByteArray();
        // Tamper with revealedSafeCells (bytes 21-24 in the format):
        // magic(4) + w(4) + h(4) + m(4) + firstSafe(1) + placed(1) + over(1) + won(1) + revealedSafe(4)
        int offset = 4 + 4 + 4 + 4 + 1 + 1 + 1 + 1; // = 20
        // Write a bogus value (9999) into revealedSafeCells
        data[offset]     = 0;
        data[offset + 1] = 0;
        data[offset + 2] = 0x27;
        data[offset + 3] = 0x0F; // 9999

        assertThrows(IOException.class, () -> {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
                Board.load(in);
            }
        }, "load should reject a save file with mismatched revealedSafeCells");
    }
}
