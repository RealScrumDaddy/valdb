package ch.muhmenthaler.valdb.gui.input;

import ch.muhmenthaler.valdb.model.Tag;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.layout.FlowPane;

import java.util.List;

public class TagPillCell<S> extends TableCell<S, List<Tag>> {

    private final FlowPane pane = new FlowPane(4, 4);

    public TagPillCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        pane.prefWrapLengthProperty().bind(widthProperty().subtract(10)); // wrap within column
    }

    @Override
    protected void updateItem(List<Tag> tags, boolean empty) {
        super.updateItem(tags, empty);
        if (empty || tags == null || tags.isEmpty()) {
            setGraphic(null);
            return;
        }
        pane.getChildren().setAll(tags.stream()
                .map(t -> TagPill.create(t.name(), t.color(), null))
                .toList());
        setGraphic(pane);
    }
}

