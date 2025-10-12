package dev.bossiq.minesweeper;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("Minesweeper x Solver");
        StackPane root = new StackPane(new Label("Hello Gamer !"));
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
