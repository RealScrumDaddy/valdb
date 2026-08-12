package ch.muhmenthaler.valdb.gui.dialog;

import ch.muhmenthaler.valdb.ValDBApplication;
import ch.muhmenthaler.valdb.gui.input.TagField;
import ch.muhmenthaler.valdb.gui.input.TextFieldWithAutoComplete;
import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.Tag;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.TextFields;

import java.util.*;
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
            Integer existingSourceId,
            String newSourceTitle,
            String newSourceAuthor,
            String newSourceGenre
    ) {}

    public static Optional<Input> show(List<Chapter> availableChapters, List<Tag> availableTags,
                                       List<Source> availableSources) {
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


        TextFieldWithAutoComplete newChaptersTextField = new TextFieldWithAutoComplete(availableChapters.stream().map(Chapter::title).toList());
        TagField chapterTagField = new TagField(newChaptersTextField, "add new Chapter");

        TextFieldWithAutoComplete newTagsTextField = new TextFieldWithAutoComplete(availableTags.stream().map(Tag::name).toList());
        TagField tagsTagField = new TagField(newTagsTextField, "add new Tags");

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
        grid.addRow(4, new Label("Chapters:"), chapterTagField);
        grid.addRow(5, new Label("Tags:"), tagsTagField);
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

            List<Integer> chapterIds = new ArrayList<>();
            List<String> newChapterTitles = new ArrayList<>();
            splitAgainstExisting(chapterTagField.getTagSet(), availableChapters, Chapter::id, Chapter::title, chapterIds, newChapterTitles);

            List<Integer> tagIds = new ArrayList<>();
            List<String> newTagNames = new ArrayList<>();
            splitAgainstExisting(tagsTagField.getTagSet(), availableTags, Tag::id, Tag::name, tagIds, newTagNames);

            Source selectedExisting = existingSourceBox.getValue();

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
                    selectedExisting != null ? selectedExisting.id() : null,
                    newSourceTitleField.getText(),
                    newSourceAuthorField.getText(),
                    newSourceGenreField.getText()
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