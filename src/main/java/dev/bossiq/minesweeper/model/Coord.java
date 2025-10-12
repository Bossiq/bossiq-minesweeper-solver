package dev.bossiq.minesweeper.model;

public final class Coord {
    public final int x;
    public final int y;

    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coord)) return false;
        Coord c = (Coord) o;
        return x == c.x && y == c.y;
    }

    @Override public int hashCode() {
        return 31 * x + y;
    }

    @Override public String toString() {
        return "(" + x + "," + y + ")";
    }
}
