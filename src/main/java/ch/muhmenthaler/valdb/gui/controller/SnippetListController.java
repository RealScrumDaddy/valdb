package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.gui.dialog.AddSnippetDialog;
import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.Snippet;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.Tag;
import ch.muhmenthaler.valdb.repository.ChapterRepository;
import ch.muhmenthaler.valdb.repository.SnippetRepository;
import ch.muhmenthaler.valdb.repository.SourceRepository;
import ch.muhmenthaler.valdb.repository.TagRepository;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SnippetListController {

    @FXML private TableView<Snippet> snippetTable;
    @FXML private TableColumn<Snippet, String> originalColumn;
    @FXML private TableColumn<Snippet, String> translationColumn;
    @FXML private TableColumn<Snippet, String> sourceColumn;
    @FXML private TableColumn<Snippet, String> verseColumn;
    @FXML private TableColumn<Snippet, String> pageColumn;
    @FXML private TableColumn<Snippet, String> chapterColumn;
    @FXML private TextField searchField;

    private final SnippetRepository snippetRepo = new SnippetRepository();
    private final SourceRepository sourceRepo = new SourceRepository();
    private final ChapterRepository chapterRepo = new ChapterRepository();
    private final TagRepository tagRepo = new TagRepository();
    private final ObservableList<Snippet> items = FXCollections.observableArrayList();

    private Integer projectId;
    private Map<Integer, Chapter> chaptersById = Map.of();
    private List<Chapter> chapters = List.of();
    private List<Tag> tags = List.of();
    private List<Source> sources = List.of();

    @FXML
    public void initialize() {
        originalColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().originalContent()));
        translationColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().translationContent()));
        sourceColumn.setCellValueFactory(c -> {
            var source = c.getValue().source();
            return new SimpleStringProperty(source != null ? source.title() : "");
        });
        verseColumn.setCellValueFactory(c -> {
            var verseStart = c.getValue().verseStart();
            var verseEnd = c.getValue().verseEnd();
            if (verseStart != null && verseEnd != null) {
                return new SimpleStringProperty(verseStart + "-" + verseEnd);
            }
            return new SimpleStringProperty(verseStart != null ? String.valueOf(verseStart) : "");
        });
        pageColumn.setCellValueFactory(c -> {
            Integer page = c.getValue().page();
            return new SimpleStringProperty(page != null ? String.valueOf(page) : "");
        });
        chapterColumn.setCellValueFactory(c -> {
            String joined = c.getValue().chapterIds().stream()
                    .map(chaptersById::get)
                    .filter(Objects::nonNull)
                    .map(Chapter::title)
                    .collect(Collectors.joining("; "));
            return new SimpleStringProperty(joined);
        });
        snippetTable.setItems(items);
        // No loadAll() here — we don't know which project we're showing yet.
    }

    /** Must be called once, right after this controller is loaded, before any data can show. */
    public void setProjectId(int projectId) {
        this.projectId = projectId;
        loadAll();
    }

    private record LoadResult(List<Snippet> snippets, List<Chapter> chapters, List<Tag> tags, List<Source> sources) {}

    private void loadAll() {
        runAsync(
                () -> {
                    List<Snippet> snippets = snippetRepo.loadByIds(snippetRepo.filter(projectId, null, null));
                    List<Chapter> chapterList = chapterRepo.loadByIds(chapterRepo.listByProject(projectId));
                    List<Tag> tagList = tagRepo.loadByIds(tagRepo.listByProject(projectId));
                    List<Source> sourceList = sourceRepo.loadByIds(sourceRepo.listByProject(projectId));
                    return new LoadResult(snippets, chapterList, tagList, sourceList);
                },
                result -> {
                    chapters = result.chapters();
                    tags = result.tags();
                    sources = result.sources();
                    chaptersById = new HashMap<>();
                    for (Chapter chapter : chapters) chaptersById.put(chapter.id(), chapter);
                    items.setAll(result.snippets());
                }
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
    private List<Integer> filterToProject(List<Integer> searchIds) throws SQLException {
        if (searchIds.isEmpty()) return searchIds;
        List<Integer> projectSnippetIds = snippetRepo.filter(projectId, null, null);
        return searchIds.stream().filter(projectSnippetIds::contains).toList();
    }

    @FXML
    private void onAddSnippet() {
        AddSnippetDialog.show(chapters, tags, sources).ifPresent(input -> runAsync(
                () -> {
                    Integer sourceId = resolveSourceId(input);

                    List<Integer> resolvedChapterIds = new java.util.ArrayList<>(input.chapterIds());
                    int nextChapterSortOrder = chapters.size();
                    for (String title : input.newChapterTitles()) {
                        int newId = chapterRepo.insert(projectId, title, nextChapterSortOrder++);
                        resolvedChapterIds.add(newId);
                    }

                    List<Integer> resolvedTagIds = new java.util.ArrayList<>(input.tagIds());
                    for (String name : input.newTagNames()) {
                        int newId = tagRepo.insert(projectId, name, null); // color left unset for now
                        resolvedTagIds.add(newId);
                    }

                    int snippetId = snippetRepo.insert(
                            List.of(projectId),
                            sourceId,
                            input.original(),
                            input.translation(),
                            input.verseStart(),
                            input.verseEnd(),
                            input.page()
                    );

                    for (int chapterId : resolvedChapterIds) snippetRepo.linkChapter(snippetId, chapterId);
                    for (int tagId : resolvedTagIds) snippetRepo.linkTag(snippetId, tagId);

                    return snippetId;
                },
                snippetId -> loadAll() // full reload picks up any newly created chapters/tags/source too
        ));
    }

    private Integer resolveSourceId(AddSnippetDialog.Input input) throws SQLException {
        String newTitle = input.newSourceTitle();
        if (newTitle != null && !newTitle.isBlank()) {
            return sourceRepo.insert(
                    List.of(projectId),
                    newTitle.trim(),
                    blankToNull(input.newSourceAuthor()),
                    blankToNull(input.newSourceGenre())
            );
        }
        return input.existingSourceId();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
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