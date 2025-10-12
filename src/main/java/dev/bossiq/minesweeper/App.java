package dev.bossiq.minesweeper;

import dev.bossiq.minesweeper.ui.BoardView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        BoardView root = new BoardView();
        Scene scene = new Scene(root);
        stage.setTitle("Minesweeper + Solver (Unfinished))");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
