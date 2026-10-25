package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.business.SourceService;
import ch.muhmenthaler.valdb.gui.dialog.AddSourceDialog;
import ch.muhmenthaler.valdb.gui.dialog.ConfirmDeleteDialog;
import ch.muhmenthaler.valdb.gui.input.ChapterFilterBinding;
import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.CustomFieldValue;
import ch.muhmenthaler.valdb.model.FieldDefinition;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.sourceTree.*;
import ch.muhmenthaler.valdb.repository.ChapterRepository;
import ch.muhmenthaler.valdb.repository.FieldDefinitionRepository;
import ch.muhmenthaler.valdb.repository.SnippetRepository;
import ch.muhmenthaler.valdb.repository.SourceRepository;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SourceListController {
    @FXML private TreeView<SourceTreeNode> sourceTree;
    @FXML private ComboBox<Chapter> chapterFilter;
    private final SourceRepository sourceRepo = new SourceRepository();
    private final FieldDefinitionRepository fieldDefinitionRepo = new FieldDefinitionRepository();
    private final SnippetRepository snippetRepo = new SnippetRepository();
    private final ChapterRepository chapterRepo = new ChapterRepository();
    private Integer projectId;
    private ChapterFilterBinding chapterFilterBinding;
    private final SourceService sourceService = new SourceService();
    private List<Source> allSources = List.of();
    private List<FieldDefinition> fieldDefinitions = List.of();

    private record LoadResult(List<Source> allSources, Set<Integer> visibleIds, List<FieldDefinition> fieldDefinitions) {}

    @FXML
    public void initialize() {
        sourceTree.setRoot(new TreeItem<>(new AuthorNode("Sources")));
        sourceTree.setCellFactory(tv -> new SourceTreeCell(this::persistEdit, this::deleteSource));
        this.chapterFilterBinding = new ChapterFilterBinding(chapterFilter, this::loadAll);
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
        runAsync(
                () -> chapterRepo.loadByIds(chapterRepo.listByProject(projectId)),
                chapters -> {
                    this.chapterFilterBinding.setChapters(chapters);
                    loadAll();
                }
        );
    }

    private void loadAll() {
        Chapter selectedChapter = chapterFilterBinding.getSelectedChapter();
        runAsync(
                () -> {
                    List<Source> sources = sourceRepo.loadByIds(sourceRepo.listByProject(projectId));
                    Set<Integer> visibleIds = selectedChapter == null
                            ? null
                            : new HashSet<>(snippetRepo.listSourceIdsByChapter(selectedChapter.id()));
                    List<FieldDefinition> defs = fieldDefinitionRepo.loadByIds(
                            fieldDefinitionRepo.listByProject(projectId, "source"));
                    return new LoadResult(sources, visibleIds, defs);
                },
                result -> {
                    allSources = result.allSources();
                    fieldDefinitions = result.fieldDefinitions();
                    List<Source> visible = result.visibleIds() == null
                            ? allSources
                            : allSources.stream().filter(s -> result.visibleIds().contains(s.id())).toList();
                    populateTree(visible, fieldDefinitions);
                }
        );
    }

    @FXML
    private void onAddSource() {
        AddSourceDialog.show(allSources, fieldDefinitions).ifPresent(input -> runAsync(
                () -> sourceService.create(projectId, input, fieldDefinitions.size()),
                newId -> loadAll()));
    }


    private void populateTree(List<Source> sources, List<FieldDefinition> fieldDefinitions) {
        Map<String, Boolean> expansionState = captureExpansionState();

        Map<String, List<Source>> byAuthor = new TreeMap<>();
        for (Source source : sources) {
            String author = (source.author() == null || source.author().isBlank())
                    ? "Unknown author" : source.author();
            byAuthor.computeIfAbsent(author, k -> new ArrayList<>()).add(source);
        }
        TreeItem<SourceTreeNode> root = new TreeItem<>(new AuthorNode("Sources"));
        for (var entry : byAuthor.entrySet()) {
            TreeItem<SourceTreeNode> authorItem = new TreeItem<>(new AuthorNode(entry.getKey()));
            authorItem.setExpanded(expansionState.getOrDefault(getExpansionKey(authorItem.getValue()), true));
            entry.getValue().stream()
                    .sorted(Comparator.comparing(Source::title))
                    .forEach(source -> authorItem.getChildren().add(buildTitleItem(source, fieldDefinitions, expansionState)));
            root.getChildren().add(authorItem);
        }
        sourceTree.setRoot(root);
    }

    private TreeItem<SourceTreeNode> buildTitleItem(Source source, List<FieldDefinition> fieldDefinitions, Map<String, Boolean> expansionState) {
        TreeItem<SourceTreeNode> titleItem = new TreeItem<>(new TitleNode(source));
        titleItem.getChildren().add(new TreeItem<>(new GenreNode(source)));
        titleItem.setExpanded(expansionState.getOrDefault(getExpansionKey(titleItem.getValue()), false));

        Map<Integer, CustomFieldValue> valuesByDefinitionId = source.customFields().stream().collect(
                Collectors.toMap(CustomFieldValue::fieldDefinitionId, v -> v));

        for (FieldDefinition fieldDefinition : fieldDefinitions){
            CustomFieldValue fieldValue = valuesByDefinitionId.getOrDefault(
                    fieldDefinition.id(),
                    new CustomFieldValue(fieldDefinition.id(), fieldDefinition.name(), null)
            );
            titleItem.getChildren().add(new TreeItem<>(new CustomFieldNode(source, fieldValue)));
        }
        return titleItem;
    }

    private String getExpansionKey(SourceTreeNode node){
        return switch (node){
            case AuthorNode a -> "author:" + a.authorName();
            case TitleNode t -> "title:" + t.source().id();
            default -> null;
        };
    }

    private Map<String, Boolean> captureExpansionState(){
        Map<String, Boolean> expansionState = new HashMap<>();
        collectExpansionState(this.sourceTree.getRoot(), expansionState);
        return expansionState;
    }

    private void collectExpansionState(TreeItem<SourceTreeNode> tree, Map<String, Boolean> expansionState){
        if (tree == null) return;
        String key = getExpansionKey(tree.getValue());
        if (key != null) expansionState.put(key, tree.isExpanded());
        for (TreeItem<SourceTreeNode> child : tree.getChildren()){
            collectExpansionState(child, expansionState);
        }
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

    private void deleteSource(Source source) {
        runAsync(() -> sourceRepo.countCitingSnippets(source.id(), projectId),
                citing -> confirmAndDeleteSource(source, citing));
    }

    private void confirmAndDeleteSource(Source source, int citingSnippets) {
        int otherProjects = source.projectIds().size() - 1;
        boolean isOnlyProject = otherProjects == 0;

        String consequence = isOnlyProject
                ? "This source is only used in this project, and will therefore be DELETED PERMANENTLY."
                : "This source is also used in " + otherProjects + " other project(s). "
                + "It will be PERMANENTLY DELETED only from this project.";
        if (citingSnippets > 0) {
            consequence += "\n" + citingSnippets + " snippet(s) in this project cite it and will be left without a source.";
        }

        String description = source.title()
                + (source.author() != null ? "\n— " + source.author() : "");
        if (!ConfirmDeleteDialog.confirm(sourceTree.getScene().getWindow(),
                "source", description, consequence)) return;

        runAsync(() -> {
                    if (isOnlyProject) sourceRepo.delete(source.id());
                    else sourceRepo.unlinkProject(source.id(), projectId);
                    return null;
                },
                ignored -> loadAll());
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