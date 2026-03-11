package dev.bossiq.minesweeper;

import dev.bossiq.minesweeper.ui.BoardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Application entry point.
 * Creates the main window and loads the dark-theme stylesheet.
 *
 * @author Oboroceanu Marian (Bossiq)
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        BoardView root = new BoardView();
        Scene scene = new Scene(root);

        var cssUrl = getClass().getResource("/style.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("Minesweeper + Solver");
        stage.setMinWidth(480);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();

        Platform.runLater(() -> {
            stage.sizeToScene();
            stage.centerOnScreen();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
