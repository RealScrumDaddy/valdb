package ch.muhmenthaler.valdb.gui.dialog;

import ch.muhmenthaler.valdb.gui.input.TextFieldWithAutoComplete;
import ch.muhmenthaler.valdb.model.CustomFieldValue;
import ch.muhmenthaler.valdb.model.FieldDefinition;
import ch.muhmenthaler.valdb.model.Source;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dialog for creating a brand-new Source: the three hardcoded fields (title/author/genre),
 * one input per existing project-defined custom field, and a way to add fields that don't
 * exist yet — all using the same autocomplete text field so the user can reuse previously
 * entered values.
 */
public class AddSourceDialog {

    /** A brand-new field/value pair — no FieldDefinition exists for it yet, the caller must create one. */
    public record NewCustomField(String name, String value) {}

    public record Input(
            String title,
            String author,
            String genre,
            Map<Integer, String> customFieldValues,
            List<NewCustomField> newCustomFields
    ) {}

    private record CustomFieldRow(TextFieldWithAutoComplete nameField, TextFieldWithAutoComplete valueField) {}

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

        // --- Existing custom fields: one autocomplete value field per FieldDefinition already on the project ---
        Map<Integer, TextFieldWithAutoComplete> customFieldInputs = new LinkedHashMap<>();
        int row = 3;
        for (FieldDefinition def : sourceFieldDefinitions) {
            List<String> suggestions = availableSources.stream()
                    .flatMap(s -> s.customFields().stream())
                    .filter(cf -> cf.fieldDefinitionId() == def.id())
                    .map(CustomFieldValue::value)
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

        List<String> existingFieldNames = sourceFieldDefinitions.stream().map(FieldDefinition::name).toList();
        List<CustomFieldRow> newFieldRows = new ArrayList<>();
        VBox newFieldsContainer = new VBox(4);

        SimpleBooleanProperty newFieldsValid = new SimpleBooleanProperty(true);
        Runnable revalidateNewFields = () -> {
            boolean valid = newFieldRows.stream().noneMatch(r -> {
                String name = r.nameField().getText();
                String value = r.valueField().getText();
                boolean hasValue = value != null && !value.isBlank();
                boolean hasName = name != null && !name.isBlank();
                return hasValue && !hasName; // a value with no field name can't be saved
            });
            newFieldsValid.set(valid);
        };

        Button addFieldButton = new Button("+ Add custom field");
        addFieldButton.setOnAction(e -> {
            TextFieldWithAutoComplete nameField = new TextFieldWithAutoComplete(existingFieldNames);
            nameField.setPromptText("Field name");
            TextFieldWithAutoComplete valueField = new TextFieldWithAutoComplete(List.of());
            valueField.setPromptText("Value");
            Button removeButton = new Button("✕");

            HBox rowBox = new HBox(6, nameField, valueField, removeButton);
            CustomFieldRow rowData = new CustomFieldRow(nameField, valueField);

            removeButton.setOnAction(ev -> {
                newFieldsContainer.getChildren().remove(rowBox);
                newFieldRows.remove(rowData);
                revalidateNewFields.run();
                resizeToFitContent(dialog);
            });
            nameField.textProperty().addListener((obs, oldV, newV) -> revalidateNewFields.run());
            valueField.textProperty().addListener((obs, oldV, newV) -> revalidateNewFields.run());

            newFieldRows.add(rowData);
            newFieldsContainer.getChildren().add(rowBox);
            resizeToFitContent(dialog);
        });

        Label newFieldsWarningLabel = new Label("Give each new field a name before it can be saved.");
        newFieldsWarningLabel.setWrapText(true);
        newFieldsWarningLabel.setStyle("-fx-text-fill: #b00020; -fx-font-size: 11px;");
        newFieldsWarningLabel.visibleProperty().bind(newFieldsValid.not());
        newFieldsWarningLabel.managedProperty().bind(newFieldsWarningLabel.visibleProperty());

        VBox newFieldsSection = new VBox(6, newFieldsContainer, addFieldButton, newFieldsWarningLabel);
        grid.addRow(row++, new Label("New fields:"), newFieldsSection);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        var okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(titleField.textProperty().isEmpty().or(newFieldsValid.not()));

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;

            Map<Integer, String> customValues = new LinkedHashMap<>();
            for (var entry : customFieldInputs.entrySet()) {
                String value = entry.getValue().getText();
                if (value != null && !value.isBlank()) {
                    customValues.put(entry.getKey(), value.trim());
                }
            }

            // A name typed into "+ Add custom field" that matches an existing field fills that
            // field's value instead of creating a duplicate FieldDefinition.
            Map<String, Integer> fieldIdsByNameLower = sourceFieldDefinitions.stream()
                    .collect(Collectors.toMap(
                            fd -> fd.name().trim().toLowerCase(),
                            FieldDefinition::id,
                            (a, b) -> a
                    ));

            List<NewCustomField> newCustomFields = new ArrayList<>();
            for (CustomFieldRow rowData : newFieldRows) {
                String name = rowData.nameField().getText();
                String value = rowData.valueField().getText();
                if (name == null || name.isBlank() || value == null || value.isBlank()) continue;
                String trimmedName = name.trim();
                Integer existingId = fieldIdsByNameLower.get(trimmedName.toLowerCase());
                if (existingId != null) {
                    customValues.put(existingId, value.trim());
                } else {
                    newCustomFields.add(new NewCustomField(trimmedName, value.trim()));
                }
            }

            return new Input(
                    titleField.getText().trim(),
                    blankToNull(authorField.getText()),
                    blankToNull(genreField.getText()),
                    customValues,
                    newCustomFields
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

    /**
     * Re-measures the dialog's preferred size and grows the window to fit, so rows added/removed
     * after the dialog is already showing don't get clipped. Deferred via runLater because the
     * scene graph hasn't recomputed layout for the just-added/removed node yet on this call.
     */
    private static void resizeToFitContent(Dialog<?> dialog) {
        Platform.runLater(() -> {
            Window window = dialog.getDialogPane().getScene().getWindow();
            if (window != null) {
                window.sizeToScene();
            }
        });
    }
}