package dev.bossiq.minesweeper.ui;

import dev.bossiq.minesweeper.model.*;
import dev.bossiq.minesweeper.solver.Solver;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class BoardView extends BorderPane {

    private static final int TILE_SIZE = 32;

    private Board board;
    private final GridPane grid = new GridPane();
    private Button[][] tiles;

    private final Solver solver = new Solver();

    private final ComboBox<String> difficulty = new ComboBox<>();
    private final Button newGameBtn = new Button("New Game");
    private final Button stepBtn = new Button("Step Solver (S)");
    private final Button autoBtn = new Button("Auto Solve (A)");
    private final Label statusLabel = new Label();
    private final Label minesLeftLabel = new Label();
    private final Label flagsLabel = new Label();
    private final Label movesLabel = new Label();
    private final Label timeLabel = new Label();

    private Timeline timer;

    public BoardView() {
        setPadding(new Insets(10));
        buildTopBar();

        grid.setHgap(2);
        grid.setVgap(2);
        grid.setAlignment(Pos.CENTER);
        setCenter(grid);

        setFocusTraversable(true);
        setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.R) {
                startNewGame(currentDifficulty());
            } else if (ev.getCode() == KeyCode.S) {
                stepSolver();
            } else if (ev.getCode() == KeyCode.A) {
                autoSolve();
            }
        });

        startNewGame(Difficulty.BEGINNER);
        requestFocus();
    }

    private enum Difficulty { BEGINNER, INTERMEDIATE, EXPERT }
    private static class Preset { final int w,h,m; Preset(int w,int h,int m){this.w=w;this.h=h;this.m=m;} }
    private static final java.util.Map<Difficulty, Preset> PRESETS = java.util.Map.of(
            Difficulty.BEGINNER, new Preset(9, 9, 10),
            Difficulty.INTERMEDIATE, new Preset(16, 16, 40),
            Difficulty.EXPERT, new Preset(30, 16, 99)
    );

    private void buildTopBar() {
        difficulty.getItems().addAll(
                "Beginner (9x9, 10)",
                "Intermediate (16x16, 40)",
                "Expert (30x16, 99)"
        );
        difficulty.getSelectionModel().select(0);
        difficulty.setOnAction(e -> startNewGame(currentDifficulty()));
        newGameBtn.setOnAction(e -> startNewGame(currentDifficulty()));
        stepBtn.setOnAction(e -> stepSolver());
        autoBtn.setOnAction(e -> autoSolve());

        HBox left = new HBox(8, new Label("Difficulty:"), difficulty, newGameBtn, stepBtn, autoBtn);
        left.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(16, statusLabel, minesLeftLabel, flagsLabel, movesLabel, timeLabel);
        stats.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(20, left, spacer, stats);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 10, 0));

        setTop(top);
    }

    private Difficulty currentDifficulty() {
        return switch (difficulty.getSelectionModel().getSelectedIndex()) {
            case 1 -> Difficulty.INTERMEDIATE;
            case 2 -> Difficulty.EXPERT;
            default -> Difficulty.BEGINNER;
        };
    }

    private void startNewGame(Difficulty diff) {
        Preset p = PRESETS.get(diff);
        board = new Board(p.w, p.h, p.m);
        buildGrid(p.w, p.h);
        refreshAll();
        statusLabel.setText("🙂 New game — left click: reveal, right click: flag. Shortcuts: R/S/A");
        updateStats();
        startTimer(true);
        requestFocus();
    }

    private void buildGrid(int w, int h) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        tiles = new Button[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Button btn = new Button();
                btn.setPrefSize(TILE_SIZE, TILE_SIZE);
                btn.setMinSize(TILE_SIZE, TILE_SIZE);
                btn.setMaxSize(TILE_SIZE, TILE_SIZE);
                btn.setFont(Font.font("Consolas", 16));
                btn.setFocusTraversable(false);

                final int fx = x, fy = y;
                btn.setOnMouseClicked(ev -> {
                    if (board.isGameOver()) return;

                    if (ev.getButton() == MouseButton.PRIMARY) {
                        Board.RevealResult r = board.reveal(fx, fy);
                        if (r.hitMine) {
                            refreshAll();
                            statusLabel.setText("💥 Boom! You hit a mine. (R to restart)");
                            startTimer(false);
                        } else {
                            for (Coord c : r.revealed) refreshTile(c.x(), c.y());
                            if (board.isWon()) {
                                statusLabel.setText("🏆 You win! (R to play again)");
                                startTimer(false);
                                refreshAll();
                            } else {
                                statusLabel.setText("🙂");
                            }
                        }
                        updateStats();
                    } else if (ev.getButton() == MouseButton.SECONDARY) {
                        board.toggleFlag(fx, fy);
                        refreshTile(fx, fy);
                        updateStats();
                    }
                });

                tiles[y][x] = btn;
                grid.add(btn, x, y);
            }
        }
    }

    private void refreshAll() {
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                refreshTile(x, y);
            }
        }
    }

    private void refreshTile(int x, int y) {
        Cell cell = board.getCell(x, y);
        Button b = tiles[y][x];

        b.setDisable(cell.isRevealed());

        String style = "-fx-font-weight: bold; -fx-alignment: center;";
        String text;

        if (cell.isRevealed()) {
            if (cell.isMine()) {
                text = "💣";
                style += " -fx-background-color: #ffebee;";
            } else {
                int n = cell.getAdjacentMines();
                if (n > 0) {
                    text = Integer.toString(n);
                    style += " -fx-text-fill: " + colorFor(n) + "; -fx-background-color: #fafafa;";
                } else {
                    text = "";
                    style += " -fx-background-color: #fafafa;";
                }
            }
        } else {
            text = cell.isFlagged() ? "🚩" : "";
            style += " -fx-background-color: #e0e0e0;";
        }

        b.setText(text);
        b.setStyle(style);
    }

    private String colorFor(int n) {
        return switch (n) {
            case 1 -> "#1565c0";
            case 2 -> "#2e7d32";
            case 3 -> "#c62828";
            case 4 -> "#283593";
            case 5 -> "#6d4c41";
            case 6 -> "#00838f";
            case 7 -> "#000000";
            case 8 -> "#424242";
            default -> "#000000";
        };
    }

    private void updateStats() {
        GameStats s = board.snapshotStats();
        minesLeftLabel.setText("Mines left: " + board.getMinesRemaining());
        flagsLabel.setText("Flags: " + s.flagsUsed());
        movesLabel.setText("Moves: " + s.moves());
        timeLabel.setText("Time: " + String.format("%.0f s", s.elapsedSeconds()));
    }

    private void startTimer(boolean restart) {
        if (timer != null) timer.stop();
        if (!restart) return;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e ->
                timeLabel.setText("Time: " + String.format("%.0f s", board.snapshotStats().elapsedSeconds()))
        ));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.playFromStart();
    }

    private void stepSolver() {
        if (board.isGameOver()) return;
        int actions = solver.step(board);
        if (actions == 0) {
            statusLabel.setText("🤔 Solver stuck—need a guess or more info.");
        } else {
            statusLabel.setText("🧠 Solver made " + actions + " action(s).");
        }
        refreshAll();
        updateStats();
        if (board.isGameOver()) startTimer(false);
        requestFocus();
    }

    private void autoSolve() {
        if (board.isGameOver()) return;
        int actions = solver.autoSolve(board);
        if (board.isWon()) {
            statusLabel.setText("🏆 Solver cleared the board!");
        } else if (actions == 0) {
            statusLabel.setText("🤔 Solver stuck—no deterministic moves.");
        } else {
            statusLabel.setText("🧠 Solver made " + actions + " action(s). Done.");
        }
        refreshAll();
        updateStats();
        if (board.isGameOver()) startTimer(false);
        requestFocus();
    }
}
