package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.business.AppLogger;
import ch.muhmenthaler.valdb.gui.dialog.NewProjectDialog;
import ch.muhmenthaler.valdb.gui.settings.UiSettings;
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
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;

public class MainController {

    public enum ViewKind { SOURCES, SNIPPETS }
    public record ViewRequest(Project project, ViewKind viewKind) {}

    @FXML private VBox sidebar;
    @FXML private Button collapseButton;
    @FXML private ListView<Project> projectList;
    @FXML private HBox viewSelector;
    @FXML private ToggleButton sourcesToggle;
    @FXML private ToggleButton snippetsToggle;
    @FXML private Button newProjectButton;
    @FXML private StackPane contentArea;
    @FXML private Slider fontSizeSlider;

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

        UiSettings ui = UiSettings.get();
        ui.applyFontSize(contentArea);

        // Slider: range comes from UiSettings so the limits live in one place
        fontSizeSlider.setMin(UiSettings.MIN_FONT_SIZE);
        fontSizeSlider.setMax(UiSettings.MAX_FONT_SIZE);
        fontSizeSlider.setMajorTickUnit(1);
        fontSizeSlider.setMinorTickCount(0);
        fontSizeSlider.setSnapToTicks(true);
        fontSizeSlider.setBlockIncrement(1);
        fontSizeSlider.valueProperty().bindBidirectional(ui.fontSizeProperty());
        fontSizeSlider.setTooltip(new Tooltip("Text size — Ctrl +/−, Ctrl 0 or double-click to reset"));
        fontSizeSlider.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) ui.resetFontSize();
        });

        // Ctrl/Cmd + mouse wheel over the content
        contentArea.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (!e.isShortcutDown()) return;
            ui.changeFontSize(e.getDeltaY() > 0 ? 1 : -1);
            e.consume();
        });

        // Keyboard shortcuts (the scene doesn't exist yet during initialize)
        contentArea.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            var acc = scene.getAccelerators();
            acc.put(new KeyCharacterCombination("+", KeyCombination.SHORTCUT_DOWN), () -> ui.changeFontSize(1));
            acc.put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.SHORTCUT_DOWN), () -> ui.changeFontSize(1));
            acc.put(new KeyCharacterCombination("-", KeyCombination.SHORTCUT_DOWN), () -> ui.changeFontSize(-1));
            acc.put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.SHORTCUT_DOWN), () -> ui.changeFontSize(-1));
            acc.put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), ui::resetFontSize);
        });
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
                        projectList.getSelectionModel().select(newProjects.getFirst());
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
        boolean showViewSelector = selectedProject != null;
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
            AppLogger.get().log(Level.SEVERE, task.getException().getMessage());
            new Alert(Alert.AlertType.ERROR, "Database error: " + task.getException().getMessage()).showAndWait();
        });
        new Thread(task).start();
    }
}