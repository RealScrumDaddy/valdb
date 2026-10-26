package ch.muhmenthaler.valdb.gui.input;

import javafx.scene.Cursor;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class ResizeHandle {

    private ResizeHandle() {}

    /** Wraps the area with a thin strip below it that resizes the area's height when dragged. */
    public static VBox wrap(TextArea area) {
        Region handle = new Region();
        handle.setPrefHeight(6);
        handle.setMinHeight(6);
        handle.setCursor(Cursor.V_RESIZE);
        handle.getStyleClass().add("resize-handle");

        double[] start = new double[2]; // [mouse y, area height] when the drag began
        handle.setOnMousePressed(e -> {
            start[0] = e.getScreenY();
            start[1] = area.getHeight();
        });
        handle.setOnMouseDragged(e -> {
            double height = Math.max(40, start[1] + e.getScreenY() - start[0]);
            area.setPrefHeight(height);
            area.setMinHeight(height);
        });
        return new VBox(area, handle);
    }
}
