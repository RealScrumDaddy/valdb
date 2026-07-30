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
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainController {

    public enum ViewKind { SOURCES, SNIPPETS }
    public record ViewRequest(Project project, ViewKind viewKind) {}

    @FXML private VBox sidebar;
    @FXML private Button collapseButton;
    @FXML private ListView<Project> projectList;
    @FXML private VBox viewSelector;
    @FXML private ToggleButton sourcesToggle;
    @FXML private ToggleButton snippetsToggle;
    @FXML private Button newProjectButton;
    @FXML private StackPane contentArea;

    private final ProjectRepository projectRepo = new ProjectRepository();
    private final ObservableList<Project> items = FXCollections.observableArrayList();
    private final ToggleGroup viewToggleGroup = new ToggleGroup();

    private static final double EXPANDED_WIDTH = 220;
    private static final double COLLAPSED_WIDTH = 48;
    private boolean collapsed = false;

    private Project selectedProject;
    private Consumer<ViewRequest> onViewRequested;

    @FXML
    public void initialize() {
        projectList.setItems(items);
        projectList.setCellFactory(list -> new ListCell<>() {
            private final Label label = new Label();
            private final javafx.scene.layout.StackPane card = new javafx.scene.layout.StackPane(label);

            {
                card.getStyleClass().add("project-card");
                label.getStyleClass().add("project-card-label");
                setPadding(javafx.geometry.Insets.EMPTY);
            }

            @Override
            protected void updateItem(Project project, boolean empty) {
                super.updateItem(project, empty);
                if (empty || project == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    label.setText(project.name());
                    setGraphic(card);
                    setText(null);
                }
            }
        });

        sourcesToggle.setToggleGroup(viewToggleGroup);
        snippetsToggle.setToggleGroup(viewToggleGroup);

        projectList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            selectedProject = newVal;
            viewSelector.setVisible(true);
            viewSelector.setManaged(true);
            sourcesToggle.setSelected(true); // default view whenever a (possibly different) project is picked
            fireViewRequest(ViewKind.SOURCES);
        });

        viewToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null || selectedProject == null) return;
            ViewKind kind = newToggle == sourcesToggle ? ViewKind.SOURCES : ViewKind.SNIPPETS;
            fireViewRequest(kind);
        });

        loadProjects();
    }

    private void fireViewRequest(ViewKind kind) {
        if (onViewRequested != null) {
            onViewRequested.accept(new ViewRequest(selectedProject, kind));
        }
    }

    /** Called whenever the person picks a project or switches between Sources/Snippets. */
    public void setOnViewRequested(Consumer<ViewRequest> callback) {
        this.onViewRequested = callback;
    }

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
        boolean showViewSelector = !collapsed && selectedProject != null;
        viewSelector.setVisible(showViewSelector);
        viewSelector.setManaged(showViewSelector);
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