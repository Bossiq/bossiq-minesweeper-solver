package dev.bossiq.minesweeper.model;

import java.io.*;
import java.util.*;

/**
 * Core Minesweeper board model.
 * <p>
 * Manages mine placement, cell reveals (with flood-fill for zero-cells),
 * chording, flagging, win/loss detection, and binary save/load.
 * Supports first-click safety and seeded RNG for reproducible boards.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public final class Board {

    /**
     * Result of a {@link #reveal(int, int)} or {@link #chord(int, int)} call.
     * Contains whether a mine was hit and which cells were newly revealed.
     */
    public static final class RevealResult {
        public final boolean hitMine;
        public final List<Coord> revealed;

        public RevealResult(boolean hitMine, List<Coord> revealed) {
            this.hitMine = hitMine;
            this.revealed = revealed;
        }
    }

    /** Magic bytes identifying the .msw save format. */
    private static final int SAVE_MAGIC = 0x4D535731;

    private final int width;
    private final int height;
    private final int mineCount;
    private final Cell[][] grid;

    private boolean gameOver   = false;
    private boolean won        = false;
    private int revealedSafeCells = 0;
    private int flagsUsed      = 0;
    private int moves          = 0;
    private long startNanos    = 0L;
    private long endNanos      = 0L;

    private final Random rng;
    private final boolean firstClickSafe;
    private boolean minesPlaced;

    // ────────────────────────────────────────────────────────
    //  Constructors
    // ────────────────────────────────────────────────────────

    /** Creates a board with random RNG and first-click safety enabled. */
    public Board(int width, int height, int mineCount) {
        this(width, height, mineCount, new Random(), true);
    }

    /** Creates a seeded board with first-click safety enabled. */
    public Board(int width, int height, int mineCount, long seed) {
        this(width, height, mineCount, new Random(seed), true);
    }

    /** Creates a board with a custom RNG and first-click safety enabled. */
    public Board(int width, int height, int mineCount, Random rng) {
        this(width, height, mineCount, rng, true);
    }

    /**
     * Full constructor.
     *
     * @param width          board width in cells
     * @param height         board height in cells
     * @param mineCount      number of mines (must be 1 .. cells-1)
     * @param rng            random number generator (for shuffling mine placement)
     * @param firstClickSafe if true, mines are placed <em>after</em> the first
     *                       reveal so the clicked cell is guaranteed safe
     */
    public Board(int width, int height, int mineCount, Random rng, boolean firstClickSafe) {
        validateSizes(width, height, mineCount);
        this.width  = width;
        this.height = height;
        this.mineCount = mineCount;
        this.rng = Objects.requireNonNull(rng);
        this.firstClickSafe = firstClickSafe;
        this.grid = new Cell[height][width];

        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                grid[y][x] = new Cell();

        if (!firstClickSafe) {
            placeMines(Collections.emptySet());
            computeAdjacencyCounts();
            minesPlaced = true;
        } else {
            minesPlaced = false;
        }
    }

    /** Internal constructor used by {@link #load(DataInput)}. */
    private Board(int width, int height, int mineCount,
                  boolean firstClickSafe, boolean minesPlaced,
                  Random rng, boolean skipInit) {
        validateSizes(width, height, mineCount);
        this.width  = width;
        this.height = height;
        this.mineCount = mineCount;
        this.firstClickSafe = firstClickSafe;
        this.minesPlaced = minesPlaced;
        this.rng = Objects.requireNonNull(rng);
        this.grid = new Cell[height][width];

        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                grid[y][x] = new Cell();
    }

    // ────────────────────────────────────────────────────────
    //  Accessors
    // ────────────────────────────────────────────────────────

    public int     getWidth()          { return width; }
    public int     getHeight()         { return height; }
    public int     getMineCount()      { return mineCount; }
    public boolean isGameOver()        { return gameOver; }
    public boolean isWon()             { return won; }
    public int     getMoves()          { return moves; }
    public int     getFlagsUsed()      { return flagsUsed; }
    public int     getMinesRemaining() { return mineCount - flagsUsed; }
    public boolean areMinesPlaced()    { return minesPlaced; }

    /** Returns the cell at (x, y). Throws if out of bounds. */
    public Cell getCell(int x, int y) {
        boundsCheck(x, y);
        return grid[y][x];
    }

    // ────────────────────────────────────────────────────────
    //  Core gameplay
    // ────────────────────────────────────────────────────────

    /**
     * Reveals the cell at (x, y). If it's a zero-cell, flood-fills
     * all connected safe cells. Returns the result.
     */
    public RevealResult reveal(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return new RevealResult(false, List.of());
        if (!minesPlaced && firstClickSafe) ensureMinesPlacedExcluding(x, y);

        Cell cell = grid[y][x];
        if (cell.isRevealed() || cell.isFlagged())
            return new RevealResult(false, List.of());

        moves++;
        ensureStarted();

        if (cell.isMine()) {
            cell.reveal();
            gameOver = true;
            won = false;
            endNanos = System.nanoTime();
            revealAllMines();
            return new RevealResult(true, List.of(new Coord(x, y)));
        }

        List<Coord> changed = new ArrayList<>();
        floodReveal(x, y, changed);
        checkWin();
        return new RevealResult(false, changed);
    }

    /**
     * Chords the cell at (x, y): if it's a revealed number whose flag
     * count matches, reveals all remaining hidden neighbours.
     */
    public RevealResult chord(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return new RevealResult(false, List.of());

        Cell center = grid[y][x];
        if (!center.isRevealed() || center.isMine())
            return new RevealResult(false, List.of());

        int number = center.getAdjacentMines();
        if (number == 0) return new RevealResult(false, List.of());

        List<Coord> neigh = neighbors(x, y);
        int flagged = 0;
        List<Coord> candidates = new ArrayList<>();
        for (Coord c : neigh) {
            Cell nc = grid[c.y()][c.x()];
            if (nc.isFlagged()) flagged++;
            else if (!nc.isRevealed()) candidates.add(c);
        }
        if (flagged != number || candidates.isEmpty())
            return new RevealResult(false, List.of());

        moves++;
        ensureStarted();

        List<Coord> changed = new ArrayList<>();
        for (Coord c : candidates) {
            Cell nc = grid[c.y()][c.x()];
            if (nc.isFlagged() || nc.isRevealed()) continue;
            if (nc.isMine()) {
                nc.reveal();
                gameOver = true;
                won = false;
                endNanos = System.nanoTime();
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

    /**
     * Toggles the flag on the cell at (x, y).
     *
     * @return {@code true} if the cell is now flagged, {@code false} otherwise
     */
    public boolean toggleFlag(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return false;
        Cell c = grid[y][x];
        if (c.isRevealed()) return false;

        moves++;
        ensureStarted();
        boolean was = c.isFlagged();
        c.toggleFlag();
        if (was) flagsUsed--;
        else flagsUsed++;
        return c.isFlagged();
    }

    /** Clears all flags without counting as moves. Returns flags removed. */
    public int clearAllFlags() {
        int cleared = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                Cell c = grid[y][x];
                if (c.isFlagged()) { c.toggleFlag(); cleared++; }
            }
        flagsUsed = 0;
        return cleared;
    }

    // ────────────────────────────────────────────────────────
    //  Flood-fill reveal (BFS)
    // ────────────────────────────────────────────────────────

    private void floodReveal(int sx, int sy, List<Coord> changed) {
        Deque<Coord> dq = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        dq.add(new Coord(sx, sy));
        visited.add(sy * width + sx);

        while (!dq.isEmpty()) {
            Coord cur = dq.removeFirst();
            Cell cell = grid[cur.y()][cur.x()];
            if (cell.isRevealed() || cell.isFlagged()) continue;

            cell.reveal();
            changed.add(cur);
            if (!cell.isMine()) revealedSafeCells++;

            // Only expand neighbours if this cell has no adjacent mines
            if (cell.getAdjacentMines() == 0) {
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cur.x() + dx, ny = cur.y() + dy;
                        if (inBounds(nx, ny)) {
                            int key = ny * width + nx;
                            if (visited.contains(key)) continue;
                            Cell n = grid[ny][nx];
                            if (!n.isRevealed() && !n.isFlagged() && !n.isMine()) {
                                visited.add(key);
                                dq.addLast(new Coord(nx, ny));
                            }
                        }
                    }
            }
        }
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    private static void validateSizes(int width, int height, int mineCount) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("width/height must be > 0");
        int cells = width * height;
        if (mineCount <= 0 || mineCount >= cells)
            throw new IllegalArgumentException("mineCount must be 1.." + (cells - 1));
    }

    private void placeMines(Set<Integer> exclude) {
        int cells = width * height;
        List<Integer> idxs = new ArrayList<>(cells);
        for (int i = 0; i < cells; i++)
            if (!exclude.contains(i)) idxs.add(i);
        if (idxs.size() < mineCount)
            throw new IllegalStateException("Not enough cells after exclusions");
        Collections.shuffle(idxs, rng);
        for (int i = 0; i < mineCount; i++) {
            int idx = idxs.get(i), mx = idx % width, my = idx / width;
            grid[my][mx].setMine(true);
        }
    }

    private void computeAdjacencyCounts() {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                if (grid[y][x].isMine()) { grid[y][x].setAdjacentMines(0); continue; }
                int c = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (inBounds(nx, ny) && grid[ny][nx].isMine()) c++;
                    }
                grid[y][x].setAdjacentMines(c);
            }
    }

    private void ensureMinesPlacedExcluding(int sx, int sy) {
        if (minesPlaced) return;
        placeMines(Collections.singleton(sy * width + sx));
        computeAdjacencyCounts();
        minesPlaced = true;
    }

    private void revealAllMines() {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                Cell c = grid[y][x];
                if (c.isMine() && !c.isRevealed()) c.reveal();
            }
    }

    private void checkWin() {
        if (revealedSafeCells == width * height - mineCount) {
            won = true;
            gameOver = true;
            endNanos = System.nanoTime();
        }
    }

    private void ensureStarted() {
        if (startNanos == 0L) startNanos = System.nanoTime();
    }

    List<Coord> neighbors(int x, int y) {
        List<Coord> res = new ArrayList<>(8);
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (inBounds(nx, ny)) res.add(new Coord(nx, ny));
            }
        return res;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void boundsCheck(int x, int y) {
        if (!inBounds(x, y))
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") out of bounds");
    }

    // ────────────────────────────────────────────────────────
    //  Statistics snapshot
    // ────────────────────────────────────────────────────────

    /** Returns a snapshot of the current game statistics. */
    public GameStats snapshotStats() {
        long end = (endNanos == 0L ? System.nanoTime() : endNanos);
        double sec = (startNanos == 0L) ? 0.0 : (end - startNanos) / 1_000_000_000.0;
        int totalSafe = width * height - mineCount;
        return new GameStats(width, height, mineCount, totalSafe,
                revealedSafeCells, flagsUsed, moves, gameOver, won, sec);
    }

    // ────────────────────────────────────────────────────────
    //  Save / Load (.msw binary format)
    // ────────────────────────────────────────────────────────

    /** Serialises the board state to a binary stream. */
    public void save(DataOutput out) throws IOException {
        GameStats s = snapshotStats();
        out.writeInt(SAVE_MAGIC);
        out.writeInt(width);
        out.writeInt(height);
        out.writeInt(mineCount);
        out.writeBoolean(firstClickSafe);
        out.writeBoolean(minesPlaced);
        out.writeBoolean(gameOver);
        out.writeBoolean(won);
        out.writeInt(revealedSafeCells);
        out.writeInt(flagsUsed);
        out.writeInt(moves);
        out.writeDouble(s.elapsedSeconds());

        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                Cell c = grid[y][x];
                out.writeBoolean(c.isMine());
                out.writeByte(c.getAdjacentMines());
                byte st = (byte) (c.isRevealed() ? 1 : (c.isFlagged() ? 2 : 0));
                out.writeByte(st);
            }
    }

    /** Deserialises a board state from a binary stream. */
    public static Board load(DataInput in) throws IOException {
        int magic = in.readInt();
        if (magic != SAVE_MAGIC)
            throw new IOException("Not a Minesweeper save file");

        int w = in.readInt(), h = in.readInt(), m = in.readInt();
        boolean firstSafe = in.readBoolean(), placed = in.readBoolean();
        boolean over = in.readBoolean(), won = in.readBoolean();
        int revealedSafe = in.readInt(), flags = in.readInt(), moves = in.readInt();
        double elapsed = in.readDouble();

        Board b = new Board(w, h, m, firstSafe, placed, new Random(), true);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                boolean mine = in.readBoolean();
                int adj = in.readByte() & 0xFF;
                int st  = in.readByte() & 0xFF;
                Cell c = b.grid[y][x];
                c.setMine(mine);
                c.setAdjacentMines(adj);
                c.forceSetState(st == 1 ? CellState.REVEALED
                              : st == 2 ? CellState.FLAGGED
                              : CellState.HIDDEN);
            }

        // Validate grid state against header counters
        int actualRevealed = 0, actualFlags = 0, actualMines = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                Cell c = b.grid[y][x];
                if (c.isRevealed() && !c.isMine()) actualRevealed++;
                if (c.isFlagged()) actualFlags++;
                if (c.isMine()) actualMines++;
            }
        if (actualRevealed != revealedSafe)
            throw new IOException("Corrupt save: revealedSafeCells=" + revealedSafe
                    + " but actual=" + actualRevealed);
        if (actualFlags != flags)
            throw new IOException("Corrupt save: flagsUsed=" + flags
                    + " but actual=" + actualFlags);
        if (actualMines != m)
            throw new IOException("Corrupt save: mineCount=" + m
                    + " but actual=" + actualMines);

        b.gameOver = over;
        b.won = won;
        b.revealedSafeCells = revealedSafe;
        b.flagsUsed = flags;
        b.moves = moves;

        long now = System.nanoTime();
        b.startNanos = now - (long) (elapsed * 1_000_000_000L);
        b.endNanos   = over ? now : 0L;
        return b;
    }
}
