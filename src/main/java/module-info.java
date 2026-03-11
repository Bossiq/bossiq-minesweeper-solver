/**
 * Java module descriptor for the Minesweeper application.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
module dev.bossiq.minesweeper {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;
    requires javafx.swing;

    opens   dev.bossiq.minesweeper    to javafx.graphics;
    exports dev.bossiq.minesweeper;
    exports dev.bossiq.minesweeper.model;
    exports dev.bossiq.minesweeper.solver;
    exports dev.bossiq.minesweeper.ui;
}
