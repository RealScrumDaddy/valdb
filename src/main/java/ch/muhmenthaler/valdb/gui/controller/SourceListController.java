package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.repository.SourceRepository;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SourceListController {

    @FXML private TreeView<String> sourceTree;

    private final SourceRepository sourceRepo = new SourceRepository();
    private Integer projectId;

    @FXML
    public void initialize() {
        sourceTree.setRoot(new TreeItem<>("Sources"));
        // No load here — projectId isn't known yet; setProjectId triggers the first load.
    }

    /** Must be called once, right after this controller is loaded, before any data can show. */
    public void setProjectId(int projectId) {
        this.projectId = projectId;
        loadAll();
    }

    private void loadAll() {
        runAsync(
                () -> sourceRepo.loadByIds(sourceRepo.listByProject(projectId)),
                this::populateTree
        );
    }

    private void populateTree(List<Source> sources) {
        // TreeMap sorts author names alphabetically for free; "Unknown author" sorts
        // in with the rest rather than needing special-cased null handling.
        Map<String, List<Source>> byAuthor = new TreeMap<>();
        for (Source source : sources) {
            String author = (source.author() == null || source.author().isBlank())
                    ? "Unknown author" : source.author();
            byAuthor.computeIfAbsent(author, k -> new java.util.ArrayList<>()).add(source);
        }

        TreeItem<String> root = new TreeItem<>("Sources");
        for (var entry : byAuthor.entrySet()) {
            TreeItem<String> authorItem = new TreeItem<>(entry.getKey());
            authorItem.setExpanded(true);
            entry.getValue().stream()
                    .sorted(Comparator.comparing(Source::title))
                    .forEach(source -> authorItem.getChildren().add(new TreeItem<>(source.title())));
            root.getChildren().add(authorItem);
        }
        sourceTree.setRoot(root);
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