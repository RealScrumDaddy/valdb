package ch.muhmenthaler.valdb.gui.dialog;

import ch.muhmenthaler.valdb.gui.input.TextFieldWithAutoComplete;
import ch.muhmenthaler.valdb.model.FieldDefinition;
import ch.muhmenthaler.valdb.model.Source;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.*;
import java.util.function.Function;

/**
 * Dialog for creating a brand-new Source: the three hardcoded fields (title/author/genre)
 * plus one input per project-defined custom field, all using the same autocomplete text field
 * so the user can reuse previously-entered values.
 */
public class AddSourceDialog {

    public record Input(
            String title,
            String author,
            String genre,
            Map<Integer, String> customFieldValues
    ) {}

    public static Optional<Input> show(List<Source> availableSources, List<FieldDefinition> sourceFieldDefinitions) {
        Dialog<Input> dialog = new Dialog<>();
        dialog.setTitle("New source");
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(AddSourceDialog.class.getResource("/ch/muhmenthaler/valdb/views/tagfield.css")).toExternalForm()
        );
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(AddSourceDialog.class.getResource("/ch/muhmenthaler/valdb/views/main.css")).toExternalForm()
        );

        TextFieldWithAutoComplete titleField = new TextFieldWithAutoComplete(distinctNonBlank(availableSources, Source::title));
        titleField.setPromptText("Title");

        TextFieldWithAutoComplete authorField = new TextFieldWithAutoComplete(distinctNonBlank(availableSources, Source::author));
        authorField.setPromptText("Author (optional)");

        TextFieldWithAutoComplete genreField = new TextFieldWithAutoComplete(distinctNonBlank(availableSources, Source::genre));
        genreField.setPromptText("Genre (optional)");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Title:"), titleField);
        grid.addRow(1, new Label("Author:"), authorField);
        grid.addRow(2, new Label("Genre:"), genreField);

        Map<Integer, TextFieldWithAutoComplete> customFieldInputs = new LinkedHashMap<>();
        int row = 3;
        for (FieldDefinition def : sourceFieldDefinitions) {
            List<String> suggestions = availableSources.stream()
                    .map(s -> s.customFields().get(def.name()))
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
            TextFieldWithAutoComplete valueField = new TextFieldWithAutoComplete(suggestions);
            valueField.setPromptText(def.name() + " (optional)");
            customFieldInputs.put(def.id(), valueField);
            grid.addRow(row++, new Label(def.name() + ":"), valueField);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        var okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(titleField.textProperty().isEmpty());

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            Map<Integer, String> customValues = new LinkedHashMap<>();
            for (var entry : customFieldInputs.entrySet()) {
                String value = entry.getValue().getText();
                if (value != null && !value.isBlank()) {
                    customValues.put(entry.getKey(), value.trim());
                }
            }
            return new Input(
                    titleField.getText().trim(),
                    blankToNull(authorField.getText()),
                    blankToNull(genreField.getText()),
                    customValues
            );
        });

        return dialog.showAndWait();
    }

    private static List<String> distinctNonBlank(List<Source> sources, Function<Source, String> extractor) {
        return sources.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}