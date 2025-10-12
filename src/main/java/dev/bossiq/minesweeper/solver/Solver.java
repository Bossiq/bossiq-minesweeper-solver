package dev.bossiq.minesweeper.solver;

import dev.bossiq.minesweeper.model.Board;
import dev.bossiq.minesweeper.model.Cell;
import dev.bossiq.minesweeper.model.Coord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Solver {

    public int autoSolve(Board board) {
        int total = 0;
        int changed;
        do {
            changed = step(board);
            total += changed;
        } while (changed > 0 && !board.isGameOver());
        return total;
    }

    public int step(Board board) {
        if (board.isGameOver()) return 0;

        int w = board.getWidth(), h = board.getHeight();
        Set<Coord> toFlag = new HashSet<>();
        Set<Coord> toReveal = new HashSet<>();

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

                if (flagged == number) {
                    toReveal.addAll(hiddenCells);
                }

                if (flagged + hidden == number) {
                    toFlag.addAll(hiddenCells);
                }
            }
        }

        toReveal.removeAll(toFlag);

        int actions = 0;
        // Apply flags
        for (Coord c : toFlag) {
            Cell cell = board.getCell(c.x(), c.y());
            if (!cell.isRevealed() && !cell.isFlagged()) {
                board.toggleFlag(c.x(), c.y());
                actions++;
                if (board.isGameOver()) return actions;
            }
        }

        for (Coord c : toReveal) {
            Cell cell = board.getCell(c.x(), c.y());
            if (!cell.isRevealed() && !cell.isFlagged()) {
                Board.RevealResult r = board.reveal(c.x(), c.y());
                actions += r.revealed.size();
                if (board.isGameOver()) return actions;
            }
        }
        return actions;
    }

    private static List<Coord> neighbors(int x, int y, int w, int h) {
        List<Coord> res = new ArrayList<>(8);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                    res.add(new Coord(nx, ny));
                }
            }
        }
        return res;
    }
}
