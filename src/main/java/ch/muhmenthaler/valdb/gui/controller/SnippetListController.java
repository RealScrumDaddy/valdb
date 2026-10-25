package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.business.SourceService;
import ch.muhmenthaler.valdb.gui.dialog.ConfirmDeleteDialog;
import ch.muhmenthaler.valdb.gui.dialog.SnippetDialog;
import ch.muhmenthaler.valdb.gui.form.SnippetForm;
import ch.muhmenthaler.valdb.gui.input.ChapterFilterBinding;
import ch.muhmenthaler.valdb.model.*;
import ch.muhmenthaler.valdb.repository.*;
import ch.muhmenthaler.valdb.gui.input.TagPillCell;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;

import java.sql.SQLException;
import java.util.*;
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
    @FXML private TableColumn<Snippet, List<Tag>> tagsColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<Chapter> chapterFilter;

    private final SnippetRepository snippetRepo = new SnippetRepository();
    private final SourceRepository sourceRepo = new SourceRepository();
    private final ChapterRepository chapterRepo = new ChapterRepository();
    private final TagRepository tagRepo = new TagRepository();
    private final FieldDefinitionRepository sourceFieldDefinitionRepo = new FieldDefinitionRepository();
    private final ObservableList<Snippet> items = FXCollections.observableArrayList();
    private final SourceService sourceService = new SourceService();

    private Integer projectId;
    private ChapterFilterBinding chapterFilterBinding;
    private Map<Integer, Chapter> chaptersById = Map.of();
    private List<Chapter> chapters = List.of();
    private List<Tag> tags = List.of();
    private List<Source> sources = List.of();
    private List<FieldDefinition> sourceFieldDefinitions = List.of();
    private Map<Integer, String> sourceFieldDefinitionsById = Map.of();
    private Map<Integer, Tag> tagsById = Map.of();

    private record ResolvedReferences(Integer sourceId, List<Integer> chapterIds, List<Integer> tagIds) {}

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
            Integer pageStart = c.getValue().pageStart();
            Integer pageEnd = c.getValue().pageEnd();
            if (pageStart != null && pageEnd != null) {
                return new SimpleStringProperty(pageStart + "-" + pageEnd);
            }
            return new SimpleStringProperty(pageStart != null ? String.valueOf(pageStart) : "");
        });
        chapterColumn.setCellValueFactory(c -> {
            String joined = c.getValue().chapterIds().stream()
                    .map(chaptersById::get)
                    .filter(Objects::nonNull)
                    .map(Chapter::title)
                    .collect(Collectors.joining("; "));
            return new SimpleStringProperty(joined);
        });
        tagsColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(
                c.getValue().tagIds().stream()
                        .map(tagsById::get)
                        .filter(Objects::nonNull)
                        .toList()));
        tagsColumn.setCellFactory(col -> new TagPillCell<>());
        snippetTable.setItems(items);
        chapterFilterBinding = new ChapterFilterBinding(chapterFilter, this::loadAll);
        snippetTable.setRowFactory(table -> {
            TableRow<Snippet> row = new TableRow<>();

            MenuItem editItem = new MenuItem("Edit snippet…");
            editItem.setOnAction(e -> editSnippet(row.getItem()));
            MenuItem deleteItem = new MenuItem("Delete snippet…");
            deleteItem.setOnAction(e -> deleteSnippet(row.getItem()));
            ContextMenu menu = new ContextMenu(editItem, deleteItem);
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));

            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    editSnippet(row.getItem());
                }
            });
            return row;
        });
    }

    /** Must be called once, right after this controller is loaded, before any data can show. */
    public void setProjectId(int projectId) {
        this.projectId = projectId;
        runAsync(
                () -> this.chapterRepo.loadByIds(this.chapterRepo.listByProject(projectId)),
                chapters -> {
                    chapterFilterBinding.setChapters(chapters);
                    loadAll();
        });
    }

    private record LoadResult(List<Snippet> snippets, List<Chapter> chapters, List<Tag> tags, List<Source> sources, List<FieldDefinition> sourceFieldDefinitions) {}

    private void loadAll() {
        Chapter selectedChapter = this.chapterFilterBinding.getSelectedChapter();
        Integer selectedChapterId = selectedChapter == null ? null : selectedChapter.id();
        runAsync(
                () -> {
                    List<Snippet> snippets = snippetRepo.loadByIds(snippetRepo.filter(projectId, selectedChapterId, null));
                    List<Chapter> chapterList = chapterRepo.loadByIds(chapterRepo.listByProject(projectId));
                    List<Tag> tagList = tagRepo.loadByIds(tagRepo.listByProject(projectId));
                    List<Source> sourceList = sourceRepo.loadByIds(sourceRepo.listByProject(projectId));
                    List<FieldDefinition> sourceFieldDefinitionList = sourceFieldDefinitionRepo.loadByIds(sourceFieldDefinitionRepo.listByProject(projectId, "source"));
                    return new LoadResult(snippets, chapterList, tagList, sourceList, sourceFieldDefinitionList);
                },
                result -> {
                    chapters = result.chapters();
                    tags = result.tags();
                    sources = result.sources();
                    sourceFieldDefinitions = result.sourceFieldDefinitions();
                    chaptersById = new HashMap<>();
                    for (Chapter chapter : chapters) chaptersById.put(chapter.id(), chapter);
                    sourceFieldDefinitionsById = new HashMap<>();
                    for (FieldDefinition sourceFieldDef : sourceFieldDefinitions) sourceFieldDefinitionsById.put(sourceFieldDef.id(), sourceFieldDef.name());
                    tagsById = new HashMap<>();
                    for (Tag tag : tags) tagsById.put(tag.id(), tag);
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
        SnippetDialog.showAdd(chapters, tags, sources, sourceFieldDefinitions).ifPresent(input -> runAsync(
                () -> {
                    ResolvedReferences refs = resolveReferences(input);
                    int snippetId = snippetRepo.insert(List.of(projectId), refs.sourceId(),
                            input.original(), input.translation(),
                            input.verseStart(), input.verseEnd(), input.pageStart(), input.pageEnd());
                    snippetRepo.setChapters(snippetId, refs.chapterIds());
                    snippetRepo.setTags(snippetId, refs.tagIds());
                    return snippetId;
                },
                snippetId -> loadAll()
        ));
    }

    private void editSnippet(Snippet snippet) {
        SnippetDialog.showEdit(snippet, chapters, tags, sources, sourceFieldDefinitions)
                .ifPresent(input -> saveEdit(snippet.id(), input));
    }

    private void deleteSnippet(Snippet snippet) {
        int otherProjects = snippet.projectIds().size() - 1;
        boolean isOnlyProject = otherProjects == 0;

        String consequence = isOnlyProject
                ? "This snippet is only used in this project, and will therefore be DELETED PERMANENTLY."
                : "This snippet is also used in " + otherProjects + " other project(s). "
                + "It will be PERMANENTLY DELETED only from this project.";

        String headerKind = isOnlyProject ? "snippet" : "snippet from this project";

        if (!ConfirmDeleteDialog.confirm(snippetTable.getScene().getWindow(),
                headerKind, describe(snippet), consequence)) return;

        runAsync(() -> {
                    if (isOnlyProject) snippetRepo.delete(snippet.id());
                    else snippetRepo.unlinkProject(snippet.id(), projectId);
                    return null;
                },
                ignored -> loadAll());
    }

    private String describe(Snippet snippet) {
        String originalText = Objects.requireNonNullElse(snippet.originalContent(), "");
        if (originalText.length() > 80) originalText = originalText.substring(0, 80) + "…";
        String translationText = Objects.requireNonNullElse(snippet.translationContent(), "");
        if (translationText.length() > 80) translationText = translationText.substring(0, 80) + "…";
        String source = snippet.source() != null ? snippet.source().title() : "no source";
        return "“" + originalText + "”\n" + "“" + translationText + "”\n— "+ source;
    }

    private void saveEdit(int snippetId, SnippetForm.Input input) {
        runAsync(
                () -> {
                    ResolvedReferences refs = resolveReferences(input);
                    snippetRepo.update(snippetId, refs.sourceId(),
                            input.original(), input.translation(),
                            input.verseStart(), input.verseEnd(), input.pageStart(), input.pageEnd());
                    snippetRepo.setChapters(snippetId, refs.chapterIds());
                    snippetRepo.setTags(snippetId, refs.tagIds());
                    return null;
                },
                ignored -> loadAll()
        );
    }

    private Integer resolveSourceId(SnippetForm.Input input) throws SQLException {
        if (input.existingSourceId() != null) return input.existingSourceId();
        if (input.newSource() == null) return null;
        return sourceService.create(projectId, input.newSource(), sourceFieldDefinitions.size());
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

    private ResolvedReferences resolveReferences(SnippetForm.Input input) throws SQLException {
        Integer sourceId = resolveSourceId(input);

        List<Integer> chapterIds = new ArrayList<>(input.chapterIds());
        int nextChapterSortOrder = chapters.size();
        for (String title : input.newChapterTitles()) {
            chapterIds.add(chapterRepo.insert(projectId, title, nextChapterSortOrder++));
        }

        List<Integer> tagIds = new ArrayList<>(input.tagIds());
        for (String name : input.newTagNames()) {
            tagIds.add(tagRepo.insert(projectId, name, null)); // color left unset for now
        }
        return new ResolvedReferences(sourceId, chapterIds, tagIds);
    }
}