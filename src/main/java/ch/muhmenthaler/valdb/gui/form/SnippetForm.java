package ch.muhmenthaler.valdb.gui.form;

import ch.muhmenthaler.valdb.gui.dialog.AddSourceDialog;
import ch.muhmenthaler.valdb.gui.input.TagField;
import ch.muhmenthaler.valdb.gui.input.TextFieldWithAutoComplete;
import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.FieldDefinition;
import ch.muhmenthaler.valdb.model.Snippet;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.Tag;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SnippetForm extends GridPane {

    public record Input(
            String original,
            String translation,
            Integer verseStart,
            Integer verseEnd,
            Integer pageStart,
            Integer pageEnd,
            List<Integer> chapterIds,
            List<String> newChapterTitles,
            List<Integer> tagIds,
            List<String> newTagNames,
            /** set when an existing source was picked; null otherwise */
            Integer existingSourceId,
            /** set when the "New Source" dialog was used to build a brand-new source; null otherwise */
            AddSourceDialog.Input newSource
    ) {}

    private record VerseOrPageRange(Integer start, Integer end) {}

    private final List<Chapter> availableChapters;
    private final List<Tag> availableTags;

    private final TextArea originalField = new TextArea();
    private final TextArea translationField = new TextArea();
    private final TextField verseField = new TextField();
    private final TextField pageField = new TextField();
    private final TagField chapterField;
    private final TagField tagField;
    private final TextFieldWithAutoComplete sourceField;

    private final Map<String, Integer> sourceIdsByTitleLower;
    /** A brand-new source built via "New Source…", not saved yet. */
    private final ObjectProperty<AddSourceDialog.Input> pendingNewSource = new SimpleObjectProperty<>();
    /** The snippet's source when editing: keeps its exact id even if two sources share a title. */
    private Source prefilledSource;

    private final BooleanBinding valid;

    public SnippetForm(List<Chapter> availableChapters, List<Tag> availableTags,
                       List<Source> availableSources, List<FieldDefinition> sourceFieldDefinitions) {
        this.availableChapters = availableChapters;
        this.availableTags = availableTags;

        originalField.setWrapText(true);
        translationField.setWrapText(true);
        verseField.setPromptText("e.g. 12 or 57-65");
        pageField.setPromptText("e.g. 12 or 57-65");

        chapterField = new TagField(
                new TextFieldWithAutoComplete(availableChapters.stream().map(Chapter::title).toList()),
                "add new Chapter");
        tagField = new TagField(
                new TextFieldWithAutoComplete(availableTags.stream().map(Tag::name).toList()),
                "add new Tags");

        // --- Source: pick an existing one via autocomplete, or create a brand-new one in its own dialog ---
        sourceIdsByTitleLower = availableSources.stream().collect(Collectors.toMap(
                s -> s.title().trim().toLowerCase(), Source::id, (a, b) -> a));

        sourceField = new TextFieldWithAutoComplete(availableSources.stream().map(Source::title).toList());
        sourceField.setPromptText("Existing source (leave blank for none)");

        Button newSourceButton = new Button("New Source…");
        newSourceButton.setOnAction(e ->
                AddSourceDialog.show(availableSources, sourceFieldDefinitions).ifPresent(newSource -> {
                    pendingNewSource.set(newSource);
                    sourceField.setText(newSource.title());
                }));

        sourceField.textProperty().addListener((obs, oldText, newText) -> {
            AddSourceDialog.Input pending = pendingNewSource.get();
            if (pending != null && !pending.title().equals(newText)) pendingNewSource.set(null);
        });

        Label sourceHintLabel = new Label();
        sourceHintLabel.textProperty().bind(
                Bindings.when(pendingNewSource.isNotNull()).then("(new)").otherwise(""));

        // Existing sources only: free text that matches nothing is invalid; new ones go through "New Source…"
        BooleanBinding sourceValid = Bindings.createBooleanBinding(() -> {
            String text = sourceField.getText();
            if (text == null || text.isBlank()) return true;
            if (isPendingNewSource(text) || isPrefilledSource(text)) return true;
            return sourceIdsByTitleLower.containsKey(text.trim().toLowerCase());
        }, sourceField.textProperty(), pendingNewSource);

        BooleanBinding numbersValid = Bindings.createBooleanBinding(
                () -> parseVerseOrPageRange(verseField.getText()) != null &&
                        parseVerseOrPageRange(pageField.getText()) != null,
                verseField.textProperty(), pageField.textProperty());

        HBox sourceRow = new HBox(6, sourceField, newSourceButton, sourceHintLabel);
        sourceRow.setAlignment(Pos.CENTER_LEFT);
        VBox sourceBox = new VBox(4, sourceRow, warningLabel(
                "No existing source matches this title. Use \"New Source…\" to create one, or clear the field.",
                sourceValid.not()));
        VBox pageBox = new VBox(4, pageField, warningLabel(
                "Verse and page must look like 12 or 57-65",
                numbersValid.not()));

        setHgap(8);
        setVgap(8);
        setPadding(new Insets(8));
        addRow(0, new Label("Original:"), originalField);
        addRow(1, new Label("Translation:"), translationField);
        addRow(2, new Label("Verse:"), verseField);
        addRow(3, new Label("Page:"), pageBox);
        addRow(4, new Label("Chapters:"), chapterField);
        addRow(5, new Label("Tags:"), tagField);
        addRow(6, new Label("Source:"), sourceBox);

        valid = originalField.textProperty().isEmpty().not().and(sourceValid).and(numbersValid);
    }

    /** True when the form can be saved. */
    public BooleanBinding validProperty() {
        return valid;
    }

    /** Fills every field from an existing snippet, for editing. */
    public void setSnippet(Snippet snippet) {
        originalField.setText(Objects.requireNonNullElse(snippet.originalContent(), ""));
        translationField.setText(Objects.requireNonNullElse(snippet.translationContent(), ""));
        verseField.setText(formatVerseOrPageRange(snippet.verseStart(), snippet.verseEnd()));
        pageField.setText(formatVerseOrPageRange(snippet.pageStart(), snippet.pageEnd()));
        chapterField.setTags(namesFor(snippet.chapterIds(), availableChapters, Chapter::id, Chapter::title));
        tagField.setTags(namesFor(snippet.tagIds(), availableTags, Tag::id, Tag::name));

        pendingNewSource.set(null);
        prefilledSource = snippet.source(); // set before the text, so validation already knows it
        sourceField.setText(prefilledSource == null ? "" : prefilledSource.title());
    }

    /** Reads the current field values. Only meaningful while validProperty() is true. */
    public Input getInput() {
        VerseOrPageRange verse = Objects.requireNonNullElse(parseVerseOrPageRange(verseField.getText()), new VerseOrPageRange(null, null));
        VerseOrPageRange page = Objects.requireNonNullElse(parseVerseOrPageRange(pageField.getText()), new VerseOrPageRange(null, null));

        List<Integer> chapterIds = new ArrayList<>();
        List<String> newChapterTitles = new ArrayList<>();
        splitAgainstExisting(chapterField.getTagSet(), availableChapters, Chapter::id, Chapter::title,
                chapterIds, newChapterTitles);

        List<Integer> tagIds = new ArrayList<>();
        List<String> newTagNames = new ArrayList<>();
        splitAgainstExisting(tagField.getTagSet(), availableTags, Tag::id, Tag::name, tagIds, newTagNames);

        Integer existingSourceId = null;
        AddSourceDialog.Input newSource = null;
        String sourceText = sourceField.getText();
        if (sourceText != null && !sourceText.isBlank()) {
            if (isPendingNewSource(sourceText)) {
                newSource = pendingNewSource.get();
            } else if (isPrefilledSource(sourceText)) {
                existingSourceId = prefilledSource.id();
            } else {
                existingSourceId = sourceIdsByTitleLower.get(sourceText.trim().toLowerCase());
            }
        }

        return new Input(
                originalField.getText(),
                translationField.getText(),
                verse.start(),
                verse.end(),
                page.start(),
                page.end(),
                chapterIds,
                newChapterTitles,
                tagIds,
                newTagNames,
                existingSourceId,
                newSource
        );
    }

    private boolean isPendingNewSource(String text) {
        AddSourceDialog.Input pending = pendingNewSource.get();
        return pending != null && pending.title().equals(text);
    }

    private boolean isPrefilledSource(String text) {
        return prefilledSource != null && prefilledSource.title().equals(text);
    }

    private static Label warningLabel(String text, BooleanBinding showWhen) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #b00020; -fx-font-size: 0.85em;");
        label.visibleProperty().bind(showWhen);
        // managed tied to visible, so a hidden warning leaves no blank gap
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    private static <T> List<String> namesFor(List<Integer> ids, List<T> available,
                                             Function<T, Integer> idExtractor, Function<T, String> nameExtractor) {
        Map<Integer, String> namesById = available.stream()
                .collect(Collectors.toMap(idExtractor, nameExtractor, (a, b) -> a));
        return ids.stream().map(namesById::get).filter(Objects::nonNull).toList();
    }

    private static <T> void splitAgainstExisting(List<String> entries, List<T> available,
                                                 Function<T, Integer> idExtractor, Function<T, String> nameExtractor,
                                                 List<Integer> outExistingIds, List<String> outNewNames) {
        Map<String, Integer> byName = available.stream().collect(Collectors.toMap(
                item -> nameExtractor.apply(item).trim().toLowerCase(), idExtractor, (a, b) -> a));
        for (String entry : entries) {
            Integer id = byName.get(entry.trim().toLowerCase());
            if (id != null) outExistingIds.add(id);
            else outNewNames.add(entry.trim());
        }
    }

    private static String formatVerseOrPageRange(Integer start, Integer end) {
        if (start == null) return "";
        return end == null ? String.valueOf(start) : start + "-" + end;
    }

    /** Accepts "", "12" or "57-65". Returns null if the text is invalid. */
    private static VerseOrPageRange parseVerseOrPageRange(String text) {
        if (text == null || text.isBlank()) return new VerseOrPageRange(null, null);
        String trimmed = text.trim();
        try {
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                return new VerseOrPageRange(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
            return new VerseOrPageRange(Integer.parseInt(trimmed), null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}