package ch.muhmenthaler.valdb;

import ch.muhmenthaler.valdb.gui.controller.MainController;
import ch.muhmenthaler.valdb.gui.controller.SnippetListController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ValDBApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader mainLoader = new FXMLLoader(ValDBApplication.class.getResource("views/MainView.fxml"));
        Parent root = mainLoader.load();
        MainController mainController = mainLoader.getController();

        mainController.setOnProjectSelected(project -> {
            try {
                FXMLLoader snippetLoader = new FXMLLoader(ValDBApplication.class.getResource("views/TableView.fxml"));
                Parent snippetView = snippetLoader.load();
                SnippetListController snippetController = snippetLoader.getController();
                snippetController.setProjectId(project.id());

                mainController.getContentArea().getChildren().setAll(snippetView);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load snippet view", e);
            }
        });

        stage.setTitle("ValDB!");
        stage.setScene(new Scene(root));
        stage.show();
    }
}