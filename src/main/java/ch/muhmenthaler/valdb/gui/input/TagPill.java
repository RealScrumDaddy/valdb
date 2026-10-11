package ch.muhmenthaler.valdb.gui.input;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public final class TagPill {

    private TagPill() {}

    /** @param onRemove null for a read-only pill (no × button) */
    public static HBox create(String text, String color, Consumer<Node> onRemove) {
        Label label = new Label(text);
        label.getStyleClass().add("tag-label");

        HBox pill = new HBox(label);
        pill.getStyleClass().add("tag-pill");
        pill.setUserData(text);
        pill.setAlignment(Pos.CENTER);
        if (color != null && !color.isBlank()) {
            pill.setStyle("-fx-background-color: " + color + ";");
        }

        if (onRemove != null) {
            Button removeBtn = new Button("×");
            removeBtn.getStyleClass().add("tag-remove-button");
            removeBtn.setOnAction(e -> onRemove.accept(pill));
            pill.getChildren().add(removeBtn);
        }
        return pill;
    }
}