package dev.bossiq.minesweeper.model;

import java.util.*;

public final class Board {

    public static final class RevealResult {
        public final boolean hitMine;
        public final List<Coord> revealed;

        public RevealResult(boolean hitMine, List<Coord> revealed) {
            this.hitMine = hitMine;
            this.revealed = revealed;
        }
    }

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


    public Board(int width, int height, int mineCount) {
        this(width, height, mineCount, new Random());
    }

    public Board(int width, int height, int mineCount, Random rng) {
        validateSizes(width, height, mineCount);
        this.width = width;
        this.height = height;
        this.mineCount = mineCount;
        this.rng = Objects.requireNonNull(rng, "rng");
        this.grid = new Cell[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid[y][x] = new Cell();
        }
        placeMines();
        computeAdjacencyCounts();
    }


    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getMineCount() { return mineCount; }
    public boolean isGameOver() { return gameOver; }
    public boolean isWon() { return won; }
    public int getMoves() { return moves; }
    public int getFlagsUsed() { return flagsUsed; }

    public Cell getCell(int x, int y) {
        boundsCheck(x, y);
        return grid[y][x];
    }


    private static void validateSizes(int width, int height, int mineCount) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width/height must be > 0");
        int cells = width * height;
        if (mineCount <= 0 || mineCount >= cells) throw new IllegalArgumentException("mineCount must be between 1 and cells-1");
    }

    private void placeMines() {
        int cells = width * height;
        List<Integer> indices = new ArrayList<>(cells);
        for (int i = 0; i < cells; i++) indices.add(i);
        Collections.shuffle(indices, rng);

        for (int i = 0; i < mineCount; i++) {
            int idx = indices.get(i);
            int x = idx % width;
            int y = idx / width;
            grid[y][x].setMine(true);
        }
    }

    private void computeAdjacencyCounts() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x].isMine()) {
                    grid[y][x].setAdjacentMines(0);
                    continue;
                }
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (inBounds(nx, ny) && grid[ny][nx].isMine()) count++;
                    }
                }
                grid[y][x].setAdjacentMines(count);
            }
        }
    }


    public RevealResult reveal(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return new RevealResult(false, Collections.emptyList());

        Cell cell = grid[y][x];
        if (cell.isRevealed() || cell.isFlagged()) {
            return new RevealResult(false, Collections.emptyList());
        }

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

    public boolean toggleFlag(int x, int y) {
        boundsCheck(x, y);
        if (gameOver) return false;

        Cell c = grid[y][x];
        if (c.isRevealed()) return c.isFlagged();

        moves++;
        ensureStarted();

        boolean wasFlagged = c.isFlagged();
        c.toggleFlag();
        if (wasFlagged) flagsUsed--;
        else flagsUsed++;
        return c.isFlagged();
    }


    private void floodReveal(int sx, int sy, List<Coord> changed) {
        Deque<Coord> dq = new ArrayDeque<>();
        dq.add(new Coord(sx, sy));

        while (!dq.isEmpty()) {
            Coord cur = dq.removeFirst();
            Cell cell = grid[cur.y][cur.x];

            if (cell.isRevealed() || cell.isFlagged()) continue;
            cell.reveal();
            changed.add(cur);

            if (!cell.isMine()) revealedSafeCells++;

            if (cell.getAdjacentMines() == 0) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cur.x + dx, ny = cur.y + dy;
                        if (inBounds(nx, ny)) {
                            Cell n = grid[ny][nx];
                            if (!n.isRevealed() && !n.isFlagged() && !n.isMine()) {
                                dq.addLast(new Coord(nx, ny));
                            } else if (!n.isMine() && n.isHidden() && n.getAdjacentMines() > 0) {

                                n.reveal();
                                changed.add(new Coord(nx, ny));
                                revealedSafeCells++;
                            }
                        }
                    }
                }
            }
        }
    }

    private void revealAllMines() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Cell c = grid[y][x];
                if (c.isMine() && !c.isRevealed()) {
                    c.reveal();
                }
            }
        }
    }

    private void checkWin() {
        int totalSafe = width * height - mineCount;
        if (revealedSafeCells == totalSafe) {
            won = true;
            gameOver = true;
            endNanos = System.nanoTime();
        }
    }

    private void ensureStarted() {
        if (startNanos == 0L) startNanos = System.nanoTime();
    }


    private boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
    private void boundsCheck(int x, int y) {
        if (!inBounds(x, y)) throw new IndexOutOfBoundsException("Out of bounds: " + x + "," + y);
    }


    public GameStats snapshotStats() {
        long end = (endNanos == 0L ? System.nanoTime() : endNanos);
        double seconds = (startNanos == 0L) ? 0.0 : (end - startNanos) / 1_000_000_000.0;
        int totalSafe = width * height - mineCount;

        return new GameStats(
                width, height, mineCount,
                totalSafe, revealedSafeCells,
                flagsUsed, moves,
                isGameOver(), isWon(),
                seconds
        );
    }
}

