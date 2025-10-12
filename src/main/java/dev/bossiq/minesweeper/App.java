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

        //TEMP
        
        dev.bossiq.minesweeper.model.Board b = new dev.bossiq.minesweeper.model.Board(9, 9, 10);
        System.out.println("Created 9x9 with 10 mines");

        dev.bossiq.minesweeper.model.Board.RevealResult r = b.reveal(0, 0);
        System.out.println("Hit mine? " + r.hitMine + " changed=" + r.revealed.size());

        b.toggleFlag(1, 1);
        b.toggleFlag(1, 1);

        System.out.println(b.snapshotStats());

        //TEMP
    }
    public static void main(String[] args) {
        launch(args);
    }
}
