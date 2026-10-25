package ch.muhmenthaler.valdb.gui.dialog;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

public final class ConfirmDeleteDialog {

    private ConfirmDeleteDialog() {}

    /**
     * @param itemKind    e.g. "snippet", "project"
     * @param description identifies the item, e.g. a text preview or the project name
     * @param consequence extra warning, e.g. "All 42 snippets in it will be deleted too." (may be null)
     * @return true only if the user explicitly chose Delete
     */
    public static boolean confirm(Window owner, String itemKind, String description, String consequence) {
        ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.WARNING, null, delete, cancel);
        alert.initOwner(owner);
        alert.setTitle("Delete " + itemKind);
        alert.setHeaderText("Permanently delete this " + itemKind + "?");

        StringBuilder text = new StringBuilder(description);
        if (consequence != null) text.append("\n\n").append(consequence);
        text.append("\n\nThis cannot be undone.");
        alert.setContentText(text.toString());

        ((Button) alert.getDialogPane().lookupButton(delete)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);

        return alert.showAndWait().filter(choice -> choice == delete).isPresent();
    }
}