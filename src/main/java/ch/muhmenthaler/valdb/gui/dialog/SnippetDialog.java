package ch.muhmenthaler.valdb.gui.dialog;

import ch.muhmenthaler.valdb.gui.form.SnippetForm;
import ch.muhmenthaler.valdb.gui.settings.UiSettings;
import ch.muhmenthaler.valdb.model.*;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SnippetDialog {

    public static Optional<SnippetForm.Input> showAdd(List<Chapter> chapters, List<Tag> tags,
                                                      List<Source> sources, List<FieldDefinition> sourceFieldDefinitions) {
        return show("Add snippet", "Add", new SnippetForm(chapters, tags, sources, sourceFieldDefinitions));
    }

    public static Optional<SnippetForm.Input> showEdit(Snippet snippet, List<Chapter> chapters, List<Tag> tags,
                                                       List<Source> sources, List<FieldDefinition> sourceFieldDefinitions) {
        SnippetForm form = new SnippetForm(chapters, tags, sources, sourceFieldDefinitions);
        form.setSnippet(snippet);
        return show("Edit snippet", "Save", form);
    }

    private static Optional<SnippetForm.Input> show(String title, String confirmText, SnippetForm form) {
        Dialog<SnippetForm.Input> dialog = new Dialog<>();
        UiSettings.get().applyFontSize(dialog.getDialogPane());
        dialog.setTitle(title);
        dialog.getDialogPane().getStylesheets().addAll(
                Objects.requireNonNull(SnippetDialog.class.getResource("/ch/muhmenthaler/valdb/views/tagfield.css")).toExternalForm(),
                Objects.requireNonNull(SnippetDialog.class.getResource("/ch/muhmenthaler/valdb/views/main.css")).toExternalForm());
        dialog.getDialogPane().setContent(form);

        ButtonType confirm = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(confirm).disableProperty().bind(form.validProperty().not());

        dialog.setResultConverter(button -> button == confirm ? form.getInput() : null);
        return dialog.showAndWait();
    }
}