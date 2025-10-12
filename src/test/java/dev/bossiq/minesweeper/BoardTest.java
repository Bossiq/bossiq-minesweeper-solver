package dev.bossiq.minesweeper;

import dev.bossiq.minesweeper.model.Board;
import dev.bossiq.minesweeper.model.Cell;
import org.junit.jupiter.api.Test;
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
        int mines = 0;
        for (int y=0;y<3;y++) for (int x=0;x<3;x++) if (b.getCell(x,y).isMine()) mines++;
        for (int y=0;y<3;y++) for (int x=0;x<3;x++) if (!b.getCell(x,y).isMine()) b.reveal(x,y);
        assertTrue(b.isWon());
        assertTrue(b.isGameOver());
    }

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Board b = new Board(9, 9, 10, 42L);
        b.reveal(3,3);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bos)) {
            b.save(out);
        }
        Board loaded;
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            loaded = Board.load(in);
        }
        assertEquals(b.getWidth(), loaded.getWidth());
        assertEquals(b.getHeight(), loaded.getHeight());
        assertEquals(b.getMineCount(), loaded.getMineCount());
        assertEquals(b.snapshotStats().revealedSafeCells(), loaded.snapshotStats().revealedSafeCells());
    }
}
