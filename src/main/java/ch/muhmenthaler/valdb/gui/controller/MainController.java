package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.gui.dialog.NewProjectDialog;
import ch.muhmenthaler.valdb.model.Project;
import ch.muhmenthaler.valdb.repository.ProjectRepository;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainController {

    @FXML private VBox sidebar;
    @FXML private Button collapseButton;
    @FXML private ListView<Project> projectList;
    @FXML private Button newProjectButton;
    @FXML private StackPane contentArea;

    private final ProjectRepository projectRepo = new ProjectRepository();
    private final ObservableList<Project> items = FXCollections.observableArrayList();

    private static final double EXPANDED_WIDTH = 220;
    private static final double COLLAPSED_WIDTH = 48;
    private boolean collapsed = false;

    private Consumer<Project> onProjectSelected;

    @FXML
    public void initialize() {
        projectList.setItems(items);
        projectList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Project project, boolean empty) {
                super.updateItem(project, empty);
                setText(empty || project == null ? null : project.name());
            }
        });

        projectList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onProjectSelected != null) {
                onProjectSelected.accept(newVal);
            }
        });

        loadProjects();
    }

    /** Called whenever the person picks a project from the list. */
    public void setOnProjectSelected(Consumer<Project> callback) {
        this.onProjectSelected = callback;
    }

    /** Whatever content you want to show once a project is active — the snippet list, etc. */
    public StackPane getContentArea() {
        return contentArea;
    }

    private void loadProjects() {
        runAsync(() -> projectRepo.loadByIds(projectRepo.listAll()), items::setAll);
    }

    @FXML
    private void onNewProject() {
        NewProjectDialog.show().ifPresent(input -> runAsync(
                () -> {
                    int id = projectRepo.insert(input.name(), input.description());
                    return projectRepo.loadByIds(List.of(id));
                },
                newProjects -> {
                    items.addAll(newProjects);
                    if (!newProjects.isEmpty()) {
                        projectList.getSelectionModel().select(newProjects.get(0));
                    }
                }
        ));
    }

    @FXML
    private void onToggleCollapse() {
        collapsed = !collapsed;
        double targetWidth = collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;

        projectList.setVisible(!collapsed);
        projectList.setManaged(!collapsed);
        newProjectButton.setVisible(!collapsed);
        newProjectButton.setManaged(!collapsed);
        collapseButton.setText(collapsed ? ">>" : "<<");

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(150),
                new KeyValue(sidebar.prefWidthProperty(), targetWidth)));
        timeline.play();
    }

    private <T> void runAsync(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Database error: " + task.getException().getMessage()).showAndWait();
        });
        new Thread(task).start();
    }
}
