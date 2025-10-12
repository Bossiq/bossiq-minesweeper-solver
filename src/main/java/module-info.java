module dev.bossiq.minesweeper {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml; // harmless even if you don't use FXML

    exports dev.bossiq.minesweeper;
    exports dev.bossiq.minesweeper.model;
    exports dev.bossiq.minesweeper.ui;

}
