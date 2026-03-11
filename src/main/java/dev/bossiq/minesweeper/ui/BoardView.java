package dev.bossiq.minesweeper.ui;

import dev.bossiq.minesweeper.model.Board;
import dev.bossiq.minesweeper.model.Cell;
import dev.bossiq.minesweeper.model.Coord;
import dev.bossiq.minesweeper.model.GameStats;
import dev.bossiq.minesweeper.solver.Solver;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Root view for the Minesweeper game.
 * <p>
 * Provides a dark-themed, cross-platform UI with:
 * <ul>
 *   <li>Solver integration with guess highlighting</li>
 *   <li>Custom board sizes</li>
 *   <li>Save/load, screenshot (F8)</li>
 *   <li>Question-mark mode (right-click cycling)</li>
 *   <li>Best-time tracking with JSON persistence</li>
 *   <li>Adaptive tile sizing for large boards</li>
 *   <li>Completion percentage display</li>
 *   <li>About/help dialog</li>
 * </ul>
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public class BoardView extends BorderPane {

    private static final int DEFAULT_TILE = 34;
    private static final int MIN_TILE     = 22;
    private static final int MAX_GRID_PX  = 1200; // max grid width in pixels
    private static final int ANIM_CAP     = 40;   // max tiles to animate at once

    private Board board;
    private final GridPane grid = new GridPane();
    private Button[][] tiles;
    private int tileSize = DEFAULT_TILE;

    private final Solver solver = new Solver();

    // ── Controls ──────────────────────────────────────────────
    private final ComboBox<String> difficulty   = new ComboBox<>();
    private final TextField seedField           = new TextField();
    private final Button faceBtn                = new Button("🙂");
    private final Button stepBtn                = new Button("Step (S)");
    private final Button autoBtn                = new Button("Auto (A)");
    private final Button clearBtn               = new Button("Clear (C)");
    private final Button saveBtn                = new Button("💾 Save");
    private final Button loadBtn                = new Button("📂 Load");
    private final Button screenshotBtn          = new Button("📸 F8");
    private final Button aboutBtn               = new Button("ℹ️");
    private final Label  statusLabel            = new Label();
    private final Label  minesLeftLabel         = new Label();
    private final Label  flagsLabel             = new Label();
    private final Label  movesLabel             = new Label();
    private final Label  timeLabel              = new Label();
    private final Label  bestTimeLabel          = new Label();
    private final Label  completionLabel        = new Label();

    // ── Custom board fields ──────────────────────────────────
    private final TextField customW = new TextField();
    private final TextField customH = new TextField();
    private final TextField customM = new TextField();

    // ── Overlay ──────────────────────────────────────────────
    private StackPane overlayPane;
    private VBox      overlayContent;

    private Timeline timer;

    /** Guess hint cell – highlighted in a different style. */
    private Coord guessHint;

    // ── Best times (persisted to JSON) ───────────────────────
    private final Map<String, Double> bestTimes = new LinkedHashMap<>();
    private static final Path BEST_TIMES_FILE = bestTimesPath();

    // ── Difficulty presets ────────────────────────────────────
    private record Preset(int w, int h, int m) {}
    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("Beginner (9×9, 10)",       new Preset(9, 9, 10));
        PRESETS.put("Intermediate (16×16, 40)", new Preset(16, 16, 40));
        PRESETS.put("Expert (30×16, 99)",       new Preset(30, 16, 99));
        PRESETS.put("Custom...",                null);
    }

    // ──────────────────────────────────────────────────────────
    //  Constructor
    // ──────────────────────────────────────────────────────────

    public BoardView() {
        setPadding(new Insets(12));
        getStyleClass().add("root");
        loadBestTimes();
        buildTopBar();

        grid.setHgap(2);
        grid.setVgap(2);
        grid.setAlignment(Pos.CENTER);
        grid.getStyleClass().add("grid-pane");

        overlayPane = new StackPane(grid);
        overlayPane.setAlignment(Pos.CENTER);
        setCenter(overlayPane);

        setFocusTraversable(true);
        setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.R) startNewGame();
            else if (solverRunning) return; // block all other keys during solving
            else if (ev.getCode() == KeyCode.S) stepSolver();
            else if (ev.getCode() == KeyCode.A) autoSolve();
            else if (ev.getCode() == KeyCode.C) clearFlags();
            else if (ev.getCode() == KeyCode.F5) saveGame();
            else if (ev.getCode() == KeyCode.F9) loadGame();
            else if (ev.getCode() == KeyCode.F8) saveScreenshot();
        });

        startNewGame();
        requestFocus();
    }

    // ──────────────────────────────────────────────────────────
    //  Top bar / toolbar
    // ──────────────────────────────────────────────────────────

    private void buildTopBar() {
        difficulty.getItems().addAll(PRESETS.keySet());
        difficulty.getSelectionModel().select(0);
        difficulty.setOnAction(e -> {
            if ("Custom...".equals(difficulty.getValue())) {
                showCustomDialog();
            } else {
                startNewGame();
            }
        });

        seedField.setPromptText("seed (optional)");
        seedField.setPrefWidth(100);

        customW.setPrefWidth(46); customW.setPromptText("W");
        customH.setPrefWidth(46); customH.setPromptText("H");
        customM.setPrefWidth(46); customM.setPromptText("M");

        faceBtn.getStyleClass().add("face-btn");
        faceBtn.setOnAction(e -> startNewGame());

        stepBtn.getStyleClass().add("solver-btn");
        autoBtn.getStyleClass().add("solver-btn");
        clearBtn.getStyleClass().add("clear-btn");

        stepBtn.setOnAction(e -> stepSolver());
        autoBtn.setOnAction(e -> autoSolve());
        clearBtn.setOnAction(e -> clearFlags());
        saveBtn.setOnAction(e -> saveGame());
        loadBtn.setOnAction(e -> loadGame());
        screenshotBtn.setOnAction(e -> saveScreenshot());
        aboutBtn.getStyleClass().add("face-btn");
        aboutBtn.setOnAction(e -> showAboutDialog());

        // Row 1: game controls | solver | file
        HBox gameControls = new HBox(6,
                new Label("Difficulty:"), difficulty,
                new Label("Seed:"), seedField, faceBtn
        );
        gameControls.setAlignment(Pos.CENTER_LEFT);

        HBox solverControls = new HBox(6, stepBtn, autoBtn, clearBtn);
        solverControls.setAlignment(Pos.CENTER);

        HBox fileControls = new HBox(6, saveBtn, loadBtn, screenshotBtn, aboutBtn);
        fileControls.setAlignment(Pos.CENTER_RIGHT);

        Region sp1 = new Region(), sp2 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        HBox.setHgrow(sp2, Priority.ALWAYS);

        HBox toolbar = new HBox(8, gameControls, sp1, solverControls, sp2, fileControls);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar-box");

        // Row 2: stats
        statusLabel.getStyleClass().add("status-label");
        bestTimeLabel.setStyle("-fx-text-fill: #f0c040; -fx-font-size: 12px;");
        completionLabel.setStyle("-fx-text-fill: #80b0ff; -fx-font-size: 12px;");

        HBox statsLeft = new HBox(8, statusLabel);
        statsLeft.setAlignment(Pos.CENTER_LEFT);

        HBox statsRight = new HBox(12,
                minesLeftLabel, flagsLabel, movesLabel,
                completionLabel, timeLabel, bestTimeLabel
        );
        statsRight.setAlignment(Pos.CENTER_RIGHT);

        Region ssp = new Region();
        HBox.setHgrow(ssp, Priority.ALWAYS);

        HBox statsBar = new HBox(8, statsLeft, ssp, statsRight);
        statsBar.getStyleClass().add("stats-bar");

        VBox topBox = new VBox(6, toolbar, statsBar);
        topBox.setPadding(new Insets(0, 0, 8, 0));
        setTop(topBox);
    }

    // ──────────────────────────────────────────────────────────
    //  Game lifecycle
    // ──────────────────────────────────────────────────────────

    private void startNewGame() {
        removeOverlay();
        guessHint = null;
        // Reset solver state in case user restarts mid-solve
        solverRunning = false;
        stepBtn.setDisable(false);
        autoBtn.setDisable(false);
        grid.setDisable(false);

        Preset p = currentPreset();
        if (p == null) return; // custom dialog cancelled

        String seedTxt = seedField.getText().trim();
        if (seedTxt.isEmpty()) {
            board = new Board(p.w, p.h, p.m);
        } else {
            try {
                board = new Board(p.w, p.h, p.m, Long.parseLong(seedTxt));
            } catch (NumberFormatException nfe) {
                statusLabel.setText("⚠️  Bad seed — using random.");
                board = new Board(p.w, p.h, p.m);
            }
        }

        computeTileSize(p.w);
        buildGrid(p.w, p.h);
        refreshAll();
        updateFace();
        statusLabel.setText("LMB reveal · RMB flag/? · Dbl chord · R S A C · F5 F9 F8");
        timeLabel.setText("⏱ --");
        updateStats();
        updateBestTimeLabel();
        startTimer(true);
        requestFocus();
        sizeAndCenterWindow();
    }

    /** Returns the selected preset; if "Custom...", returns whatever was last
        entered in the custom-size dialog (or null if the dialog was cancelled). */
    private Preset currentPreset() {
        String sel = difficulty.getValue();
        Preset p = PRESETS.get(sel);
        if (p != null) return p;
        // Custom: try to parse custom fields
        try {
            int w = Integer.parseInt(customW.getText().trim());
            int h = Integer.parseInt(customH.getText().trim());
            int m = Integer.parseInt(customM.getText().trim());
            if (w < 2 || h < 2 || m < 1 || m >= w * h) throw new NumberFormatException();
            return new Preset(w, h, m);
        } catch (NumberFormatException e) {
            return new Preset(9, 9, 10); // fallback
        }
    }

    private String difficultyKey() {
        Preset p = currentPreset();
        return p.w + "x" + p.h + "_" + p.m;
    }

    // ──────────────────────────────────────────────────────────
    //  Custom board dialog
    // ──────────────────────────────────────────────────────────

    private void showCustomDialog() {
        Dialog<Preset> dlg = new Dialog<>();
        dlg.setTitle("Custom Board Size");
        dlg.setHeaderText("Enter dimensions and mine count");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField wf = new TextField("16"), hf = new TextField("16"), mf = new TextField("40");
        wf.setPrefWidth(60); hf.setPrefWidth(60); mf.setPrefWidth(60);

        GridPane gp = new GridPane();
        gp.setHgap(10); gp.setVgap(10);
        gp.addRow(0, new Label("Width:"),  wf);
        gp.addRow(1, new Label("Height:"), hf);
        gp.addRow(2, new Label("Mines:"),  mf);
        dlg.getDialogPane().setContent(gp);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    int w = Integer.parseInt(wf.getText().trim());
                    int h = Integer.parseInt(hf.getText().trim());
                    int m = Integer.parseInt(mf.getText().trim());
                    if (w < 2 || h < 2 || m < 1 || m >= w * h) {
                        statusLabel.setText("⚠️ Invalid custom dimensions.");
                        return null;
                    }
                    return new Preset(w, h, m);
                } catch (NumberFormatException e) {
                    statusLabel.setText("⚠️ Invalid numbers.");
                    return null;
                }
            }
            return null;
        });

        Optional<Preset> result = dlg.showAndWait();
        result.ifPresentOrElse(p -> {
            customW.setText(String.valueOf(p.w));
            customH.setText(String.valueOf(p.h));
            customM.setText(String.valueOf(p.m));
            startNewGame();
        }, () -> difficulty.getSelectionModel().select(0));
    }

    // ──────────────────────────────────────────────────────────
    //  Adaptive tile sizing
    // ──────────────────────────────────────────────────────────

    private void computeTileSize(int cols) {
        int needed = cols * DEFAULT_TILE + (cols - 1) * 2 + 24; // grid gap + padding
        if (needed > MAX_GRID_PX) {
            tileSize = Math.max(MIN_TILE, (MAX_GRID_PX - (cols - 1) * 2 - 24) / cols);
        } else {
            tileSize = DEFAULT_TILE;
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Grid building
    // ──────────────────────────────────────────────────────────

    private void buildGrid(int w, int h) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        tiles = new Button[h][w];

        int fontSize = Math.max(11, tileSize - 18);

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                Button btn = new Button();
                btn.setPrefSize(tileSize, tileSize);
                btn.setMinSize(tileSize, tileSize);
                btn.setMaxSize(tileSize, tileSize);
                btn.setFont(Font.font("Consolas", fontSize));
                btn.setFocusTraversable(false);

                final int fx = x, fy = y;
                btn.setOnMouseClicked(ev -> onTileClick(ev, fx, fy));

                tiles[y][x] = btn;
                grid.add(btn, x, y);
            }
    }

    private void onTileClick(javafx.scene.input.MouseEvent ev, int fx, int fy) {
        if (board.isGameOver() || solverRunning) return;

        // Clear guess hint on any click
        clearGuessHint();

        // Chord: middle click or double-click on revealed number
        if (ev.getButton() == MouseButton.MIDDLE ||
            (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2)) {
            handleRevealResult(board.chord(fx, fy), "No chord possible");
            return;
        }

        if (ev.getButton() == MouseButton.PRIMARY) {
            Cell cell = board.getCell(fx, fy);
            if (cell.isFlagged()) return;
            if ("?".equals(tiles[fy][fx].getUserData()))
                tiles[fy][fx].setUserData(null);
            handleRevealResult(board.reveal(fx, fy), null);
        } else if (ev.getButton() == MouseButton.SECONDARY) {
            Cell cell = board.getCell(fx, fy);
            if (cell.isRevealed()) return;

            if (cell.isFlagged()) {
                board.toggleFlag(fx, fy);
                tiles[fy][fx].setUserData("?");
            } else if ("?".equals(tiles[fy][fx].getUserData())) {
                tiles[fy][fx].setUserData(null);
            } else {
                board.toggleFlag(fx, fy);
            }
            refreshTile(fx, fy);
            updateStats();
        }
    }

    private void handleRevealResult(Board.RevealResult r, String emptyMsg) {
        if (r.hitMine) {
            refreshAll();
            statusLabel.setText("💥 Boom! Press R or 🙂 to restart");
            startTimer(false);
            updateFace();
            showOverlay(false);
        } else {
            for (int i = 0; i < r.revealed.size(); i++) {
                Coord c = r.revealed.get(i);
                refreshTile(c.x(), c.y());
                if (i < ANIM_CAP) animateReveal(tiles[c.y()][c.x()]);
            }
            if (board.isWon()) {
                statusLabel.setText("🏆 You win!");
                startTimer(false);
                refreshAll();
                updateFace();
                recordBestTime();
                showOverlay(true);
            } else if (emptyMsg != null && r.revealed.isEmpty()) {
                statusLabel.setText("🤔 " + emptyMsg);
            }
            updateFace();
        }
        updateStats();
    }

    // ──────────────────────────────────────────────────────────
    //  Tile rendering
    // ──────────────────────────────────────────────────────────

    private void refreshAll() {
        for (int y = 0; y < board.getHeight(); y++)
            for (int x = 0; x < board.getWidth(); x++)
                refreshTile(x, y);
    }

    private void refreshTile(int x, int y) {
        Cell cell = board.getCell(x, y);
        Button b = tiles[y][x];
        b.setDisable(false);

        String text;
        String inlineStyle = "";

        if (cell.isRevealed()) {
            if (cell.isMine()) {
                text = "💣";
                b.getStyleClass().setAll("button", "tile-mine");
            } else {
                int n = cell.getAdjacentMines();
                text = n > 0 ? Integer.toString(n) : "";
                if (n > 0)
                    inlineStyle = "-fx-text-fill:" + colorFor(n) + ";-fx-font-size:" + Math.max(11, tileSize - 16) + "px;-fx-font-weight:bold;";
                b.getStyleClass().setAll("button", "tile-revealed");
            }
            b.setDisable(true);
        } else {
            if (cell.isFlagged())                        text = "🚩";
            else if ("?".equals(b.getUserData()))         text = "❓";
            else                                          text = "";

            // Highlight guess hint cell
            if (guessHint != null && guessHint.x() == x && guessHint.y() == y) {
                b.getStyleClass().setAll("button", "tile-hint");
            } else if (cell.isFlagged()) {
                b.getStyleClass().setAll("button", "tile-flagged");
            } else {
                b.getStyleClass().setAll("button", "tile-hidden");
            }
        }

        b.setText(text);
        b.setStyle(inlineStyle);
    }

    private String colorFor(int n) {
        return switch (n) {
            case 1 -> "#5cb8ff";
            case 2 -> "#50e050";
            case 3 -> "#ff5555";
            case 4 -> "#b080ff";
            case 5 -> "#ffaa44";
            case 6 -> "#44e8e8";
            case 7 -> "#e0e0e0";
            case 8 -> "#a0a0a0";
            default -> "#ffffff";
        };
    }

    // ──────────────────────────────────────────────────────────
    //  Animations
    // ──────────────────────────────────────────────────────────

    private void animateReveal(Button btn) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), btn);
        st.setFromX(0.7); st.setFromY(0.7);
        st.setToX(1.0);   st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        st.play();
    }

    // ──────────────────────────────────────────────────────────
    //  Win / loss overlay
    // ──────────────────────────────────────────────────────────

    private void showOverlay(boolean won) {
        removeOverlay();

        Label titleLbl = new Label(won ? "🏆  YOU WIN!  🏆" : "💥  GAME OVER  💥");
        titleLbl.getStyleClass().add("overlay-text");

        GameStats stats = board.snapshotStats();
        Label detail = new Label(String.format(
                "Time: %.1f s  ·  Moves: %d  ·  Mines: %d  ·  %.0f%%",
                stats.elapsedSeconds(), stats.moves(), stats.mines(), stats.completionPercent()));
        detail.setStyle("-fx-text-fill: #a0a8c0; -fx-font-size: 14px;");

        Button playAgain = new Button(won ? "Play Again" : "Try Again");
        playAgain.getStyleClass().add("overlay-btn");
        playAgain.setOnAction(e -> startNewGame());

        overlayContent = new VBox(12, titleLbl, detail, playAgain);
        overlayContent.setAlignment(Pos.CENTER);
        overlayContent.setPadding(new Insets(30));
        overlayContent.getStyleClass().add("overlay-bg");
        overlayContent.setMaxWidth(380);
        overlayContent.setMaxHeight(200);

        overlayPane.getChildren().add(overlayContent);

        FadeTransition ft = new FadeTransition(Duration.millis(300), overlayContent);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void removeOverlay() {
        if (overlayContent != null) {
            overlayPane.getChildren().remove(overlayContent);
            overlayContent = null;
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Stats & timer
    // ──────────────────────────────────────────────────────────

    private void updateStats() {
        GameStats s = board.snapshotStats();
        minesLeftLabel.setText("💣 " + board.getMinesRemaining());
        flagsLabel.setText("🚩 " + s.flagsUsed());
        movesLabel.setText("👆 " + s.moves());
        completionLabel.setText("📊 " + String.format("%.0f%%", s.completionPercent()));
        if (s.moves() > 0 || board.isGameOver())
            timeLabel.setText("⏱ " + String.format("%.0f s", s.elapsedSeconds()));
    }

    private void startTimer(boolean restart) {
        if (timer != null) { timer.stop(); timer = null; }
        if (!restart) return;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            GameStats s = board.snapshotStats();
            if (s.moves() > 0)
                timeLabel.setText("⏱ " + String.format("%.0f s", s.elapsedSeconds()));
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.playFromStart();
    }

    private void updateFace() {
        faceBtn.setText(board.isGameOver() ? (board.isWon() ? "😎" : "💥") : "🙂");
    }

    // ──────────────────────────────────────────────────────────
    //  Best time tracking (persisted to JSON)
    // ──────────────────────────────────────────────────────────

    private void recordBestTime() {
        String key = difficultyKey();
        double time = board.snapshotStats().elapsedSeconds();
        Double prev = bestTimes.get(key);
        if (prev == null || time < prev) {
            bestTimes.put(key, time);
            saveBestTimes();
        }
        updateBestTimeLabel();
    }

    private void updateBestTimeLabel() {
        Double best = bestTimes.get(difficultyKey());
        bestTimeLabel.setText(best != null
                ? "🏅 Best: " + String.format("%.1f s", best)
                : "");
    }

    private static Path bestTimesPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".minesweeper", "best_times.properties");
    }

    private void loadBestTimes() {
        if (!Files.exists(BEST_TIMES_FILE)) return;
        try (var reader = Files.newBufferedReader(BEST_TIMES_FILE)) {
            Properties props = new Properties();
            props.load(reader);
            for (String key : props.stringPropertyNames()) {
                try {
                    bestTimes.put(key, Double.parseDouble(props.getProperty(key)));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) { System.err.println("Failed to load best times: " + e.getMessage()); }
    }

    private void saveBestTimes() {
        try {
            Files.createDirectories(BEST_TIMES_FILE.getParent());
            Properties props = new Properties();
            bestTimes.forEach((k, v) -> props.setProperty(k, String.valueOf(v)));
            try (var writer = Files.newBufferedWriter(BEST_TIMES_FILE)) {
                props.store(writer, "Minesweeper best times");
            }
        } catch (IOException e) { System.err.println("Failed to save best times: " + e.getMessage()); }
    }

    // ──────────────────────────────────────────────────────────
    //  Solver (auto-clicks centre if board is untouched)
    // ──────────────────────────────────────────────────────────

    private boolean ensureFirstClick() {
        for (int y = 0; y < board.getHeight(); y++)
            for (int x = 0; x < board.getWidth(); x++)
                if (board.getCell(x, y).isRevealed()) return true;

        int cx = board.getWidth() / 2, cy = board.getHeight() / 2;
        Board.RevealResult r = board.reveal(cx, cy);
        for (int i = 0; i < r.revealed.size(); i++) {
            Coord c = r.revealed.get(i);
            refreshTile(c.x(), c.y());
            if (i < ANIM_CAP) animateReveal(tiles[c.y()][c.x()]);
        }
        updateStats();
        return !board.isGameOver();
    }

    /** Guard against re-entrant solver calls. */
    private volatile boolean solverRunning = false;

    private void stepSolver() {
        if (board.isGameOver() || solverRunning) return;
        if (!ensureFirstClick()) return;
        clearGuessHint();

        solverRunning = true;
        stepBtn.setDisable(true);
        autoBtn.setDisable(true);
        grid.setDisable(true);
        if (timer != null) timer.stop();  // prevent race with snapshotStats
        statusLabel.setText("🧠 Solving...");

        final Board b = board;
        new Thread(() -> {
            Solver.SolverResult r = solver.step(b);
            Platform.runLater(() -> {
                solverRunning = false;
                stepBtn.setDisable(false);
                autoBtn.setDisable(false);
                grid.setDisable(false);
                if (r.stuck()) {
                    showGuessHint(r);
                } else {
                    statusLabel.setText(String.format("🧠 Solver: %d flag(s), %d reveal(s)", r.flagsPlaced(), r.cellsRevealed()));
                }
                refreshAll(); updateStats();
                if (board.isGameOver()) { startTimer(false); updateFace(); showOverlay(board.isWon()); }
                else { startTimer(true); updateFace(); }
                requestFocus();
            });
        }, "solver-step").start();
    }

    private void autoSolve() {
        if (board.isGameOver() || solverRunning) return;
        if (!ensureFirstClick()) return;
        clearGuessHint();

        solverRunning = true;
        stepBtn.setDisable(true);
        autoBtn.setDisable(true);
        grid.setDisable(true);
        if (timer != null) timer.stop();  // prevent race with snapshotStats
        statusLabel.setText("🧠 Auto-solving...");

        final Board b = board;
        new Thread(() -> {
            Solver.SolverResult r = solver.autoSolve(b);
            Platform.runLater(() -> {
                solverRunning = false;
                stepBtn.setDisable(false);
                autoBtn.setDisable(false);
                grid.setDisable(false);
                if (board.isWon()) {
                    statusLabel.setText("🏆 Solver cleared the board!");
                    recordBestTime();
                } else if (r.stuck()) {
                    showGuessHint(r);
                } else {
                    statusLabel.setText(String.format("🧠 Solver: %d flag(s), %d reveal(s).", r.flagsPlaced(), r.cellsRevealed()));
                }
                refreshAll(); updateStats();
                if (board.isGameOver()) { startTimer(false); updateFace(); showOverlay(board.isWon()); }
                else { startTimer(true); updateFace(); }
                requestFocus();
            });
        }, "solver-auto").start();
    }

    /** Pulsing animation for the hint tile (stopped when hint is cleared). */
    private ScaleTransition hintPulse;

    private void showGuessHint(Solver.SolverResult r) {
        if (r.bestGuess() != null) {
            guessHint = r.bestGuess();
            refreshTile(guessHint.x(), guessHint.y());

            // Gentle pulse animation on hint tile
            Button hintBtn = tiles[guessHint.y()][guessHint.x()];
            hintPulse = new ScaleTransition(Duration.millis(600), hintBtn);
            hintPulse.setFromX(1.0); hintPulse.setFromY(1.0);
            hintPulse.setToX(1.08);  hintPulse.setToY(1.08);
            hintPulse.setCycleCount(Animation.INDEFINITE);
            hintPulse.setAutoReverse(true);
            hintPulse.setInterpolator(Interpolator.EASE_BOTH);
            hintPulse.play();

            double safeProb = (1.0 - r.bestGuessProb()) * 100;
            statusLabel.setText(String.format(
                    "🎯 Stuck! Best guess highlighted (≈%.0f%% safe). Click it or pick your own.",
                    safeProb));
        } else {
            statusLabel.setText("🤔 Solver stuck — no deterministic moves left.");
        }
    }

    private void clearGuessHint() {
        if (hintPulse != null) {
            hintPulse.stop();
            hintPulse = null;
        }
        if (guessHint != null) {
            Coord old = guessHint;
            guessHint = null;
            Button btn = tiles[old.y()][old.x()];
            btn.setScaleX(1.0);
            btn.setScaleY(1.0);
            refreshTile(old.x(), old.y());
        }
    }

    private void clearFlags() {
        int cleared = board.clearAllFlags();
        for (int y = 0; y < board.getHeight(); y++)
            for (int x = 0; x < board.getWidth(); x++)
                tiles[y][x].setUserData(null);
        statusLabel.setText("🧹 Cleared " + cleared + " flag(s) and question marks.");
        refreshAll(); updateStats(); requestFocus();
    }

    // ──────────────────────────────────────────────────────────
    //  Save / Load / Screenshot
    // ──────────────────────────────────────────────────────────

    private void saveGame() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Minesweeper Game");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Minesweeper Save (*.msw)", "*.msw"));
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try (var out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            board.save(out);
            statusLabel.setText("💾 Saved to " + f.getName());
        } catch (IOException ex) {
            statusLabel.setText("❌ Save failed: " + ex.getMessage());
        }
        requestFocus();
    }

    private void loadGame() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Minesweeper Game");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Minesweeper Save (*.msw)", "*.msw"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try (var in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            board = Board.load(in);
            removeOverlay();
            guessHint = null;
            solverRunning = false;
            stepBtn.setDisable(false);
            autoBtn.setDisable(false);
            grid.setDisable(false);
            computeTileSize(board.getWidth());
            buildGrid(board.getWidth(), board.getHeight());
            refreshAll(); updateStats(); updateFace();
            statusLabel.setText("📂 Loaded " + f.getName());
            startTimer(!board.isGameOver());
        } catch (IOException ex) {
            statusLabel.setText("❌ Load failed: " + ex.getMessage());
        }
        requestFocus();
    }

    private void saveScreenshot() {
        try {
            WritableImage img = grid.snapshot(new SnapshotParameters(), null);
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Screenshot");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image (*.png)", "*.png"));
            fc.setInitialFileName("minesweeper.png");
            File f = fc.showSaveDialog(getScene().getWindow());
            if (f == null) return;
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            statusLabel.setText("📸 Saved screenshot: " + f.getName());
        } catch (IOException ex) {
            statusLabel.setText("❌ Screenshot failed: " + ex.getMessage());
        }
        requestFocus();
    }

    // ──────────────────────────────────────────────────────────
    //  About dialog
    // ──────────────────────────────────────────────────────────

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Minesweeper + Solver");
        alert.setHeaderText("Minesweeper + Solver v1.1");
        alert.setContentText("""
                A modern Minesweeper with a three-pass deterministic solver \
                and probability-based guess hints.

                Controls:
                  LMB · Reveal cell
                  RMB · Flag → ? → Clear
                  Dbl-click / Middle · Chord
                  R · Restart    S · Solver step
                  A · Auto-solve    C · Clear flags
                  F5 · Save    F9 · Load    F8 · Screenshot

                Made by Oboroceanu Marian (@Bossiq)
                github.com/Bossiq/bossiq-minesweeper-solver""");
        alert.showAndWait();
        requestFocus();
    }

    // ──────────────────────────────────────────────────────────
    //  Window management
    // ──────────────────────────────────────────────────────────

    private void sizeAndCenterWindow() {
        Platform.runLater(() -> {
            if (getScene() == null || getScene().getWindow() == null) return;
            applyCss(); layout();
            Stage stage = (Stage) getScene().getWindow();
            stage.sizeToScene();
            stage.centerOnScreen();
        });
    }
}
