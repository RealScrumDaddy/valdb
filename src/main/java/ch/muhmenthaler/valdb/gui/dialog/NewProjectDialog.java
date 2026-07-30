package ch.muhmenthaler.valdb.gui.dialog;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class NewProjectDialog {

    public record Input(String name, String description) {}

    public static Optional<Input> show() {
        Dialog<Input> dialog = new Dialog<>();
        dialog.setTitle("New project");

        TextField nameField = new TextField();
        TextArea descriptionField = new TextArea();

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Name:"), nameField);
        grid.addRow(1, new Label("Description:"), descriptionField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(nameField.textProperty().isEmpty());

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new Input(nameField.getText(), descriptionField.getText()) : null
        );

        return dialog.showAndWait();
    }
}
