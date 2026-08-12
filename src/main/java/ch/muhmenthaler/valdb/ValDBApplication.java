package ch.muhmenthaler.valdb;

import ch.muhmenthaler.valdb.gui.controller.MainController;
import ch.muhmenthaler.valdb.gui.controller.SnippetListController;
import ch.muhmenthaler.valdb.gui.controller.SourceListController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class ValDBApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader mainLoader = new FXMLLoader(ValDBApplication.class.getResource("views/MainView.fxml"));
        Parent root = mainLoader.load();
        MainController mainController = mainLoader.getController();

        mainController.setOnViewRequested(request -> {
            try {
                Parent view;
                switch (request.viewKind()) {
                    case SOURCES -> {
                        FXMLLoader loader = new FXMLLoader(ValDBApplication.class.getResource("views/SourceListView.fxml"));
                        view = loader.load();
                        SourceListController controller = loader.getController();
                        controller.setProjectId(request.project().id());
                    }
                    case SNIPPETS -> {
                        FXMLLoader loader = new FXMLLoader(ValDBApplication.class.getResource("views/TableView.fxml"));
                        view = loader.load();
                        SnippetListController controller = loader.getController();
                        controller.setProjectId(request.project().id());
                    }
                    default -> throw new IllegalStateException("Unhandled view kind: " + request.viewKind());
                }
                mainController.getContentArea().getChildren().setAll(view);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load view", e);
            }
        });

        stage.setTitle("ValDB!");
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(ValDBApplication.class.getResource("views/main.css")).toExternalForm());
        scene.getStylesheets().add(Objects.requireNonNull(ValDBApplication.class.getResource("views/tagfield.css")).toExternalForm());
        stage.setScene(scene);
        stage.show();
    }
}