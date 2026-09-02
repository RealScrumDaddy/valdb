package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.model.CustomFieldValue;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.tree.*;
import ch.muhmenthaler.valdb.repository.SourceRepository;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SourceListController {
    @FXML private TreeView<SourceTreeNode> sourceTree;
    private final SourceRepository sourceRepo = new SourceRepository();
    private Integer projectId;

    @FXML
    public void initialize() {
        sourceTree.setRoot(new TreeItem<>(new AuthorNode("Sources")));
        sourceTree.setCellFactory(tv -> new SourceTreeCell(this::persistEdit));
    }

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
        Map<String, List<Source>> byAuthor = new TreeMap<>();
        for (Source source : sources) {
            String author = (source.author() == null || source.author().isBlank())
                    ? "Unknown author" : source.author();
            byAuthor.computeIfAbsent(author, k -> new ArrayList<>()).add(source);
        }
        TreeItem<SourceTreeNode> root = new TreeItem<>(new AuthorNode("Sources"));
        for (var entry : byAuthor.entrySet()) {
            TreeItem<SourceTreeNode> authorItem = new TreeItem<>(new AuthorNode(entry.getKey()));
            authorItem.setExpanded(true);
            entry.getValue().stream()
                    .sorted(Comparator.comparing(Source::title))
                    .forEach(source -> authorItem.getChildren().add(buildTitleItem(source)));
            root.getChildren().add(authorItem);
        }
        sourceTree.setRoot(root);
    }

    private TreeItem<SourceTreeNode> buildTitleItem(Source source) {
        TreeItem<SourceTreeNode> titleItem = new TreeItem<>(new TitleNode(source));
        if (source.genre() != null && !source.genre().isBlank()) {
            titleItem.getChildren().add(new TreeItem<>(new GenreNode(source)));
        }
        for (CustomFieldValue field : source.customFields()) {
            titleItem.getChildren().add(new TreeItem<>(new CustomFieldNode(source, field)));
        }
        return titleItem;
    }

    private void persistEdit(SourceTreeNode newValue) {
        runAsync(() -> {
            switch (newValue) {
                case GenreNode g -> sourceRepo.update(
                        g.source().id(), g.source().title(), g.source().author(), g.source().genre());
                case TitleNode t -> sourceRepo.update(
                        t.source().id(), t.source().title(), t.source().author(), t.source().genre());
                case CustomFieldNode c -> sourceRepo.setFieldValue(
                        c.source().id(), c.field().fieldDefinitionId(), c.field().value());
                default -> { /* AuthorNode isn't editable here */ }
            }
            return null;
        }, ignored -> loadAll());
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