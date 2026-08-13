package ch.muhmenthaler.valdb.gui.dialog;

import ch.muhmenthaler.valdb.gui.input.TagField;
import ch.muhmenthaler.valdb.gui.input.TextFieldWithAutoComplete;
import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.FieldDefinition;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.Tag;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AddSnippetDialog {

    public record Input(
            String original,
            String translation,
            Integer verseStart,
            Integer verseEnd,
            Integer page,
            List<Integer> chapterIds,
            List<String> newChapterTitles,
            List<Integer> tagIds,
            List<String> newTagNames,
            /** set when an existing source was picked via autocomplete; null otherwise */
            Integer existingSourceId,
            /** set when the "New Source" dialog was used to build a brand-new source; null otherwise */
            AddSourceDialog.Input newSource
    ) {}

    public static Optional<Input> show(List<Chapter> availableChapters, List<Tag> availableTags,
                                       List<Source> availableSources, List<FieldDefinition> sourceFieldDefinitions) {
        Dialog<Input> dialog = new Dialog<>();
        dialog.setTitle("Add snippet");
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(AddSnippetDialog.class.getResource("/ch/muhmenthaler/valdb/views/tagfield.css")).toExternalForm()
        );
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(AddSnippetDialog.class.getResource("/ch/muhmenthaler/valdb/views/main.css")).toExternalForm()
        );

        TextArea originalField = new TextArea();
        originalField.setWrapText(true);
        TextArea translationField = new TextArea();
        translationField.setWrapText(true);
        TextField verseField = new TextField();
        verseField.setPromptText("e.g. 12 or 57-65");
        TextField pageField = new TextField();
        pageField.setPromptText("e.g. 12");

        TextFieldWithAutoComplete newChaptersTextField = new TextFieldWithAutoComplete(availableChapters.stream().map(Chapter::title).toList());
        TagField chapterTagField = new TagField(newChaptersTextField, "add new Chapter");
        TextFieldWithAutoComplete newTagsTextField = new TextFieldWithAutoComplete(availableTags.stream().map(Tag::name).toList());
        TagField tagsTagField = new TagField(newTagsTextField, "add new Tags");

        // --- Source: pick an existing one via autocomplete, or create a brand-new one in its own dialog ---
        Map<String, Integer> sourceIdsByTitleLower = availableSources.stream()
                .collect(Collectors.toMap(
                        s -> s.title().trim().toLowerCase(),
                        Source::id,
                        (a, b) -> a
                ));

        TextFieldWithAutoComplete sourceField = new TextFieldWithAutoComplete(availableSources.stream().map(Source::title).toList());
        sourceField.setPromptText("Existing source (leave blank for none)");

        Button newSourceButton = new Button("New Source…");
        Label sourceHintLabel = new Label();

        AtomicReference<AddSourceDialog.Input> pendingNewSource = new AtomicReference<>();

        newSourceButton.setOnAction(e ->
                AddSourceDialog.show(availableSources, sourceFieldDefinitions).ifPresent(newSource -> {
                    pendingNewSource.set(newSource);
                    sourceField.setText(newSource.title());
                })
        );

        sourceField.textProperty().addListener((obs, oldText, newText) -> {
            AddSourceDialog.Input pending = pendingNewSource.get();
            if (pending != null && !pending.title().equals(newText)) {
                pendingNewSource.set(null);
            }
            sourceHintLabel.setText(pendingNewSource.get() != null ? "(new)" : "");
        });

        // Existing sources only: free text that doesn't match anything (and isn't the pending new
        // source) is invalid — new sources must go through the "New Source" dialog.
        BooleanBinding sourceValid = Bindings.createBooleanBinding(() -> {
            String text = sourceField.getText();
            if (text == null || text.isBlank()) return true;
            AddSourceDialog.Input pending = pendingNewSource.get();
            if (pending != null && pending.title().equals(text)) return true;
            return sourceIdsByTitleLower.containsKey(text.trim().toLowerCase());
        }, sourceField.textProperty());

        HBox sourceRow = new HBox(6, sourceField, newSourceButton, sourceHintLabel);
        sourceRow.setAlignment(Pos.CENTER_LEFT);

        Label sourceWarningLabel = new Label("No existing source matches this title — use \"New Source…\" to create one, or clear the field.");
        sourceWarningLabel.setWrapText(true);
        sourceWarningLabel.setStyle("-fx-text-fill: #b00020; -fx-font-size: 11px;");
        sourceWarningLabel.visibleProperty().bind(sourceValid.not());
        // managedProperty tied to visible so the row doesn't leave a blank gap when the warning is hidden
        sourceWarningLabel.managedProperty().bind(sourceWarningLabel.visibleProperty());

        VBox sourceColumnBox = new VBox(4, sourceRow, sourceWarningLabel);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Original:"), originalField);
        grid.addRow(1, new Label("Translation:"), translationField);
        grid.addRow(2, new Label("Verse:"), verseField);
        grid.addRow(3, new Label("Page:"), pageField);
        grid.addRow(4, new Label("Chapters:"), chapterTagField);
        grid.addRow(5, new Label("Tags:"), tagsTagField);
        grid.addRow(6, new Label("Source:"), sourceColumnBox);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        var okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(originalField.textProperty().isEmpty().or(sourceValid.not()));

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            int[] verse = parseVerse(verseField.getText());
            Integer page = parseIntOrNull(pageField.getText());

            List<Integer> chapterIds = new ArrayList<>();
            List<String> newChapterTitles = new ArrayList<>();
            splitAgainstExisting(chapterTagField.getTagSet(), availableChapters, Chapter::id, Chapter::title, chapterIds, newChapterTitles);

            List<Integer> tagIds = new ArrayList<>();
            List<String> newTagNames = new ArrayList<>();
            splitAgainstExisting(tagsTagField.getTagSet(), availableTags, Tag::id, Tag::name, tagIds, newTagNames);

            Integer existingSourceId = null;
            AddSourceDialog.Input newSource = null;
            String sourceText = sourceField.getText();
            if (sourceText != null && !sourceText.isBlank()) {
                AddSourceDialog.Input pending = pendingNewSource.get();
                if (pending != null && pending.title().equals(sourceText)) {
                    newSource = pending;
                } else {
                    existingSourceId = sourceIdsByTitleLower.get(sourceText.trim().toLowerCase());
                }
            }

            return new Input(
                    originalField.getText(),
                    translationField.getText(),
                    verse[0] == -1 ? null : verse[0],
                    verse[1] == -1 ? null : verse[1],
                    page,
                    chapterIds,
                    newChapterTitles,
                    tagIds,
                    newTagNames,
                    existingSourceId,
                    newSource
            );
        });
        return dialog.showAndWait();
    }

    private static <T> void splitAgainstExisting(
            List<String> entries,
            List<T> available,
            Function<T, Integer> idExtractor,
            Function<T, String> nameExtractor,
            List<Integer> outExistingIds,
            List<String> outNewNames){
        Map<String, Integer> byName = available.stream()
                .collect(Collectors.toMap(
                        item -> nameExtractor.apply(item).trim().toLowerCase(),
                        idExtractor,
                        (a, b) -> a
                ));
        for (String entry : entries){
            String key =  entry.trim().toLowerCase();
            Integer id = byName.get(key);
            if (id != null){
                outExistingIds.add(id);
            }else{
                outNewNames.add(entry.trim());
            }
        }
    }

    /** Returns {start, end} using -1 as "absent". Accepts "12" or "57-65"; anything else is treated as absent. */
    private static int[] parseVerse(String text) {
        if (text == null || text.isBlank()) return new int[]{-1, -1};
        String trimmed = text.trim();
        try {
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
            }
            return new int[]{Integer.parseInt(trimmed), -1};
        } catch (NumberFormatException e) {
            return new int[]{-1, -1};
        }
    }

    private static Integer parseIntOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}