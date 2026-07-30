package ch.muhmenthaler.valdb.gui.dialog;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class AddSnippetDialog {

    public record Input(String original, String translation, String source) {}

    public static Optional<Input> show() {
        Dialog<Input> dialog = new Dialog<>();
        dialog.setTitle("Add snippet");

        TextArea originalField = new TextArea();
        TextArea translationField = new TextArea();
        TextArea sourceField = new TextArea();

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Original:"), originalField);
        grid.addRow(1, new Label("Translation:"), translationField);
        grid.addRow(2, new Label("Source"), sourceField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new Input(originalField.getText(), translationField.getText(), sourceField.getText()) : null
        );

        return dialog.showAndWait();
    }
}
