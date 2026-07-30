package ch.muhmenthaler.valdb.gui.dialog;

import ch.muhmenthaler.valdb.gui.input.TextFieldWithAutoComplete;
import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.Tag;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.TextFields;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
            Integer existingSourceId,
            String newSourceTitle,
            String newSourceAuthor,
            String newSourceGenre
    ) {}

    public static Optional<Input> show(List<Chapter> availableChapters, List<Tag> availableTags,
                                       List<Source> availableSources) {
        Dialog<Input> dialog = new Dialog<>();
        dialog.setTitle("Add snippet");

        TextArea originalField = new TextArea();
        TextArea translationField = new TextArea();
        TextField verseField = new TextField();
        verseField.setPromptText("e.g. 12 or 57-65");
        TextField pageField = new TextField();

        ListView<Chapter> chapterList = new ListView<>();
        chapterList.getItems().setAll(availableChapters);
        chapterList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        chapterList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Chapter chapter, boolean empty) {
                super.updateItem(chapter, empty);
                setText(empty || chapter == null ? null : chapter.title());
            }
        });
        chapterList.setPrefHeight(100);

        TextFieldWithAutoComplete newChaptersField = new TextFieldWithAutoComplete(availableChapters.stream().map(Chapter::title).toList());
        newChaptersField.setPromptText("New chapters, comma separated");

        VBox chapterBox = new VBox(4, chapterList, newChaptersField);

        ListView<Tag> tagList = new ListView<>();
        tagList.getItems().setAll(availableTags);
        tagList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tagList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Tag tag, boolean empty) {
                super.updateItem(tag, empty);
                setText(empty || tag == null ? null : tag.name());
            }
        });
        tagList.setPrefHeight(100);

        TextField newTagsField = new TextField();
        newTagsField.setPromptText("New tags, comma separated");

        VBox tagBox = new VBox(4, tagList, newTagsField);

        ComboBox<Source> existingSourceBox = new ComboBox<>();
        existingSourceBox.getItems().setAll(availableSources);
        existingSourceBox.setPromptText("None");
        existingSourceBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Source source) { return source == null ? "" : source.title(); }
            @Override public Source fromString(String string) { return null; }
        });

        TextField newSourceTitleField = new TextField();
        newSourceTitleField.setPromptText("Title (leave blank to use selection above)");
        TextField newSourceAuthorField = new TextField();
        newSourceAuthorField.setPromptText("Author (optional)");
        TextField newSourceGenreField = new TextField();
        newSourceGenreField.setPromptText("Genre (optional)");

        VBox newSourceBox = new VBox(4, newSourceTitleField, newSourceAuthorField, newSourceGenreField);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Original:"), originalField);
        grid.addRow(1, new Label("Translation:"), translationField);
        grid.addRow(2, new Label("Verse:"), verseField);
        grid.addRow(3, new Label("Page:"), pageField);
        grid.addRow(4, new Label("Chapters:"), chapterBox);
        grid.addRow(5, new Label("Tags:"), tagBox);
        grid.addRow(6, new Label("Existing source:"), existingSourceBox);
        grid.addRow(7, new Label("Or new source:"), newSourceBox);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(originalField.textProperty().isEmpty());

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;

            int[] verse = parseVerse(verseField.getText());
            Integer page = parseIntOrNull(pageField.getText());

            List<Integer> chapterIds = chapterList.getSelectionModel().getSelectedItems().stream()
                    .map(Chapter::id).toList();
            List<Integer> tagIds = tagList.getSelectionModel().getSelectedItems().stream()
                    .map(Tag::id).toList();

            Source selectedExisting = existingSourceBox.getValue();

            return new Input(
                    originalField.getText(),
                    translationField.getText(),
                    verse[0] == -1 ? null : verse[0],
                    verse[1] == -1 ? null : verse[1],
                    page,
                    chapterIds,
                    splitAndClean(newChaptersField.getText()),
                    tagIds,
                    splitAndClean(newTagsField.getText()),
                    selectedExisting != null ? selectedExisting.id() : null,
                    newSourceTitleField.getText(),
                    newSourceAuthorField.getText(),
                    newSourceGenreField.getText()
            );
        });

        return dialog.showAndWait();
    }

    private static List<String> splitAndClean(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
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