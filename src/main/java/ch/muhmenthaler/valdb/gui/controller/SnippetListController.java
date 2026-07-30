package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.gui.dialog.AddSnippetDialog;
import ch.muhmenthaler.valdb.model.Snippet;
import ch.muhmenthaler.valdb.repository.SnippetRepository;
import ch.muhmenthaler.valdb.repository.SourceRepository;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SnippetListController {

    @FXML private TableView<Snippet> snippetTable;
    @FXML private TableColumn<Snippet, String> originalColumn;
    @FXML private TableColumn<Snippet, String> translationColumn;
    @FXML private TableColumn<Snippet, String> sourceColumn;
    @FXML private TextField searchField;

    private final SnippetRepository snippetRepo = new SnippetRepository();
    private final SourceRepository sourceRepo = new SourceRepository();
    private final ObservableList<Snippet> items = FXCollections.observableArrayList();

    private Integer projectId; // set via setProjectId once MainController knows which project was picked

    @FXML
    public void initialize() {
        originalColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().originalContent()));
        translationColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().translationContent()));
        sourceColumn.setCellValueFactory(c -> {
            var source = c.getValue().source();
            return new SimpleStringProperty(source != null ? source.title() : "");
        });
        snippetTable.setItems(items); // the table just displays whatever's in this list
        // No loadAll() here — we don't know which project we're showing yet.
    }

    /** Must be called once, right after this controller is loaded, before any data can show. */
    public void setProjectId(int projectId) {
        this.projectId = projectId;
        loadAll();
    }

    private void loadAll() {
        runAsync(
                () -> snippetRepo.loadByIds(snippetRepo.filter(projectId, null, null)),
                items::setAll // replaces the table's contents on the FX thread
        );
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) { loadAll(); return; }
        runAsync(
                () -> snippetRepo.loadByIds(filterToProject(snippetRepo.search(query + "*"))),
                items::setAll
        );
    }

    /** snippets_fts has no project column, so full-text search results need filtering after the fact. */
    private List<Integer> filterToProject(List<Integer> searchIds) throws java.sql.SQLException {
        if (searchIds.isEmpty()) return searchIds;
        List<Integer> projectIds = snippetRepo.filter(projectId, null, null);
        return searchIds.stream().filter(projectIds::contains).toList();
    }

    @FXML
    private void onAddSnippet() {
        AddSnippetDialog.show().ifPresent(input -> runAsync(
                () -> {
                    Integer sourceId = null;
                    String label = input.source();
                    if (label != null && !label.isBlank()) {
                        sourceId = sourceRepo.findOrCreate(projectId, label.trim());
                    }
                    int id = snippetRepo.insert(List.of(projectId), sourceId, input.original(), input.translation(),
                            null, null, null);
                    return snippetRepo.loadByIds(List.of(id));
                },
                items::addAll
        ));
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