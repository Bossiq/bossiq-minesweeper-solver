package dev.bossiq.minesweeper.model;

import java.io.*;
import java.util.*;

public final class Board {

    public static final class RevealResult {
        public final boolean hitMine;
        public final List<Coord> revealed;
        public RevealResult(boolean hitMine, List<Coord> revealed) {
            this.hitMine = hitMine; this.revealed = revealed;
        }
    }

    private static final int SAVE_MAGIC = 0x4D535731;

    private final int width;
    private final int height;
    private final int mineCount;
    private final Cell[][] grid;

    private boolean gameOver = false;
    private boolean won = false;
    private int revealedSafeCells = 0;
    private int flagsUsed = 0;
    private int moves = 0;
    private long startNanos = 0L;
    private long endNanos = 0L;

    private final Random rng;
    private final boolean firstClickSafe;
    private boolean minesPlaced;

    public Board(int width, int height, int mineCount) {
        this(width, height, mineCount, new Random(), true);
    }
    public Board(int width, int height, int mineCount, long seed) {
        this(width, height, mineCount, new Random(seed), true);
    }
    public Board(int width, int height, int mineCount, Random rng) {
        this(width, height, mineCount, rng, true);
    }
    public Board(int width, int height, int mineCount, Random rng, boolean firstClickSafe) {
        validateSizes(width, height, mineCount);
        this.width = width; this.height = height; this.mineCount = mineCount;
        this.rng = Objects.requireNonNull(rng); this.firstClickSafe = firstClickSafe;
        this.grid = new Cell[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid[y][x] = new Cell();
        if (!firstClickSafe) {
            placeMines(Collections.emptySet());
            computeAdjacencyCounts();
            minesPlaced = true;
        } else {
            minesPlaced = false;
        }
    }

    private Board(int width, int height, int mineCount, boolean firstClickSafe, boolean minesPlaced, Random rng, boolean skipInit) {
        validateSizes(width, height, mineCount);
        this.width = width; this.height = height; this.mineCount = mineCount;
        this.firstClickSafe = firstClickSafe; this.minesPlaced = minesPlaced; this.rng = Objects.requireNonNull(rng);
        this.grid = new Cell[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid[y][x] = new Cell();
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getMineCount() { return mineCount; }
    public boolean isGameOver() { return gameOver; }
    public boolean isWon() { return won; }
    public int getMoves() { return moves; }
    public int getFlagsUsed() { return flagsUsed; }
    public int getMinesRemaining() { return mineCount - flagsUsed; }
    public Cell getCell(int x, int y) { boundsCheck(x, y); return grid[y][x]; }

    private static void validateSizes(int width, int height, int mineCount) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width/height must be > 0");
        int cells = width * height;
        if (mineCount <= 0 || mineCount >= cells) throw new IllegalArgumentException("mineCount must be between 1 and cells-1");
    }

    private void placeMines(Set<Integer> exclude) {
        int cells = width * height;
        List<Integer> idxs = new ArrayList<>(cells);
        for (int i = 0; i < cells; i++) if (!exclude.contains(i)) idxs.add(i);
        if (idxs.size() < mineCount) throw new IllegalStateException("Not enough cells after exclusions");
        Collections.shuffle(idxs, rng);
        for (int i = 0; i < mineCount; i++) {
            int idx = idxs.get(i), x = idx % width, y = idx / width;
            grid[y][x].setMine(true);
        }
    }

    private void computeAdjacencyCounts() {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (grid[y][x].isMine()) { grid[y][x].setAdjacentMines(0); continue; }
            int c = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (inBounds(nx, ny) && grid[ny][nx].isMine()) c++;
            }
            grid[y][x].setAdjacentMines(c);
        }
    }

    private void ensureMinesPlacedExcluding(int sx, int sy) {
        if (minesPlaced) return;
        int safe = sy * width + sx;
        placeMines(Collections.singleton(safe));
        computeAdjacencyCounts();
        minesPlaced = true;
    }

    public RevealResult reveal(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return new RevealResult(false, List.of());
        if (!minesPlaced && firstClickSafe) ensureMinesPlacedExcluding(x, y);

        Cell cell = grid[y][x];
        if (cell.isRevealed() || cell.isFlagged()) return new RevealResult(false, List.of());

        moves++; ensureStarted();

        if (cell.isMine()) {
            cell.reveal();
            gameOver = true; won = false; endNanos = System.nanoTime();
            revealAllMines();
            return new RevealResult(true, List.of(new Coord(x, y)));
        }

        List<Coord> changed = new ArrayList<>();
        floodReveal(x, y, changed);
        checkWin();
        return new RevealResult(false, changed);
    }

    public RevealResult chord(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return new RevealResult(false, java.util.List.of());

        Cell center = grid[y][x];
        if (!center.isRevealed() || center.isMine()) return new RevealResult(false, java.util.List.of());

        int number = center.getAdjacentMines();
        if (number == 0) return new RevealResult(false, java.util.List.of());

        java.util.List<Coord> neigh = neighbors(x, y);
        int flagged = 0;
        java.util.List<Coord> candidates = new java.util.ArrayList<>();
        for (Coord c : neigh) {
            Cell nc = grid[c.y()][c.x()];
            if (nc.isFlagged()) flagged++;
            else if (!nc.isRevealed()) candidates.add(c);
        }
        if (flagged != number || candidates.isEmpty()) {
            return new RevealResult(false, java.util.List.of());
        }

        moves++;
        ensureStarted();

        java.util.List<Coord> changed = new java.util.ArrayList<>();
        for (Coord c : candidates) {
            Cell nc = grid[c.y()][c.x()];
            if (nc.isFlagged() || nc.isRevealed()) continue;
            if (nc.isMine()) {
                nc.reveal();
                gameOver = true; won = false; endNanos = System.nanoTime();
                revealAllMines();
                changed.add(c);
                return new RevealResult(true, changed);
            } else {
                floodReveal(c.x(), c.y(), changed);
            }
        }
        checkWin();
        return new RevealResult(false, changed);
    }

    private java.util.List<Coord> neighbors(int x, int y) {
        java.util.List<Coord> res = new java.util.ArrayList<>(8);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (inBounds(nx, ny)) res.add(new Coord(nx, ny));
            }
        }
        return res;
    }

    public boolean toggleFlag(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return false;
        Cell c = grid[y][x];
        if (c.isRevealed()) return c.isFlagged();
        moves++; ensureStarted();
        boolean was = c.isFlagged();
        c.toggleFlag();
        if (was) flagsUsed--; else flagsUsed++;
        return c.isFlagged();
    }

    /** Clears ALL flags without counting as moves; returns how many flags were removed. */
    public int clearAllFlags() {
        int cleared = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Cell c = grid[y][x];
            if (c.isFlagged()) { c.toggleFlag(); cleared++; }
        }
        flagsUsed = 0;
        return cleared;
    }

    private void floodReveal(int sx, int sy, List<Coord> changed) {
        Deque<Coord> dq = new ArrayDeque<>();
        dq.add(new Coord(sx, sy));
        while (!dq.isEmpty()) {
            Coord cur = dq.removeFirst();
            Cell cell = grid[cur.y()][cur.x()];
            if (cell.isRevealed() || cell.isFlagged()) continue;
            cell.reveal();
            changed.add(cur);
            if (!cell.isMine()) revealedSafeCells++;
            if (cell.getAdjacentMines() == 0) {
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cur.x() + dx, ny = cur.y() + dy;
                    if (inBounds(nx, ny)) {
                        Cell n = grid[ny][nx];
                        if (!n.isRevealed() && !n.isFlagged() && !n.isMine()) {
                            dq.addLast(new Coord(nx, ny));
                        } else if (!n.isMine() && n.isHidden() && n.getAdjacentMines() > 0) {
                            n.reveal(); changed.add(new Coord(nx, ny)); revealedSafeCells++;
                        }
                    }
                }
            }
        }
    }

    private void revealAllMines() {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Cell c = grid[y][x]; if (c.isMine() && !c.isRevealed()) c.reveal();
        }
    }

    private void checkWin() {
        int totalSafe = width * height - mineCount;
        if (revealedSafeCells == totalSafe) {
            won = true; gameOver = true; endNanos = System.nanoTime();
        }
    }

    private void ensureStarted() { if (startNanos == 0L) startNanos = System.nanoTime(); }

    private boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
    private void boundsCheck(int x, int y) { if (!inBounds(x, y)) throw new IndexOutOfBoundsException("Out of bounds: " + x + "," + y); }

    public GameStats snapshotStats() {
        long end = (endNanos == 0L ? System.nanoTime() : endNanos);
        double sec = (startNanos == 0L) ? 0.0 : (end - startNanos) / 1_000_000_000.0;
        int totalSafe = width * height - mineCount;
        return new GameStats(width, height, mineCount, totalSafe, revealedSafeCells, flagsUsed, moves, gameOver, won, sec);
    }

    public void save(DataOutput out) throws IOException {
        GameStats s = snapshotStats();
        out.writeInt(SAVE_MAGIC);
        out.writeInt(width); out.writeInt(height); out.writeInt(mineCount);
        out.writeBoolean(firstClickSafe); out.writeBoolean(minesPlaced);
        out.writeBoolean(gameOver); out.writeBoolean(won);
        out.writeInt(revealedSafeCells); out.writeInt(flagsUsed); out.writeInt(moves);
        out.writeDouble(s.elapsedSeconds());

        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Cell c = grid[y][x];
            out.writeBoolean(c.isMine());
            out.writeByte(c.getAdjacentMines());
            byte st = (byte)(c.isRevealed() ? 1 : (c.isFlagged() ? 2 : 0));
            out.writeByte(st);
        }
    }

    public static Board load(DataInput in) throws IOException {
        int magic = in.readInt();
        if (magic != SAVE_MAGIC) throw new IOException("Not a Minesweeper save");
        int w = in.readInt(), h = in.readInt(), m = in.readInt();
        boolean firstSafe = in.readBoolean(), minesPlaced = in.readBoolean();
        boolean gameOver = in.readBoolean(), won = in.readBoolean();
        int revealedSafe = in.readInt(), flagsUsed = in.readInt(), moves = in.readInt();
        double elapsedSec = in.readDouble();

        Board b = new Board(w, h, m, firstSafe, minesPlaced, new Random(), true);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean mine = in.readBoolean();
            int adj = in.readByte() & 0xFF;
            int st = in.readByte() & 0xFF;
            Cell c = b.grid[y][x];
            c.setMine(mine);
            c.setAdjacentMines(adj);
            c.forceSetState(st == 1 ? CellState.REVEALED : (st == 2 ? CellState.FLAGGED : CellState.HIDDEN));
        }

        b.gameOver = gameOver; b.won = won;
        b.revealedSafeCells = revealedSafe;
        b.flagsUsed = flagsUsed;
        b.moves = moves;
        if (gameOver) {
            long now = System.nanoTime();
            b.startNanos = now - (long)(elapsedSec * 1_000_000_000L);
            b.endNanos = now;
        } else {
            b.startNanos = System.nanoTime() - (long)(elapsedSec * 1_000_000_000L);
            b.endNanos = 0L;
        }
        return b;
    }
}
