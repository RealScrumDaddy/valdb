package ch.muhmenthaler.valdb.gui.controller;

import ch.muhmenthaler.valdb.model.CustomFieldValue;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.sourceTree.CustomFieldNode;
import ch.muhmenthaler.valdb.model.sourceTree.GenreNode;
import ch.muhmenthaler.valdb.model.sourceTree.SourceTreeNode;
import ch.muhmenthaler.valdb.model.sourceTree.TitleNode;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;

import java.util.function.Consumer;

class SourceTreeCell extends TreeCell<SourceTreeNode> {
    private final TextField editField = new TextField();
    private final ContextMenu titleMenu = new ContextMenu();
    private final ContextMenu fieldMenu = new ContextMenu();
    private final Consumer<SourceTreeNode> onCommit;
    private final Consumer<Source> onDelete;

    private boolean editingInPlace = false;

    SourceTreeCell(Consumer<SourceTreeNode> onCommit, Consumer<Source> onDelete) {
        this.onCommit = onCommit;
        this.onDelete = onDelete;

        editField.setOnAction(e -> commitInPlaceEdit());
        editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) cancelInPlaceEdit();
        });

        MenuItem deleteItem = new MenuItem("Delete source…");
        deleteItem.setOnAction(e -> {
            if (getItem() instanceof TitleNode t) onDelete.accept(t.source());
        });
        titleMenu.getItems().addAll(renameItem(), deleteItem);
        fieldMenu.getItems().add(renameItem());
    }

    private MenuItem renameItem() {
        MenuItem item = new MenuItem("Rename");
        item.setOnAction(e -> startInPlaceEdit());
        return item;
    }

    private ContextMenu menuFor(SourceTreeNode node) {
        if (node instanceof TitleNode) return titleMenu;
        if (node instanceof GenreNode || node instanceof CustomFieldNode) return fieldMenu;
        return null;
    }


    private void startInPlaceEdit() {
        if (!isEditableNode(getItem())) return;
        editingInPlace = true;
        editField.setText(currentRawValue(getItem()));
        setText(null);
        setGraphic(editField);
        editField.requestFocus();
        editField.selectAll();
    }

    private void cancelInPlaceEdit() {
        if (!editingInPlace) return;
        editingInPlace = false;
        setText(getItem() == null ? null : getItem().label());
        setGraphic(null);
    }

    private void commitInPlaceEdit() {
        if (!editingInPlace || getItem() == null) return;
        SourceTreeNode newValue = buildEditedNode(editField.getText());
        editingInPlace = false;
        getTreeItem().setValue(newValue);
        setText(newValue.label());
        setGraphic(null);
        onCommit.accept(newValue);
    }

    @Override
    protected void updateItem(SourceTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            editingInPlace = false;
            setText(null);
            setGraphic(null);
            setContextMenu(null);
        } else if (editingInPlace) {
            editField.setText(currentRawValue(item));
            setText(null);
            setGraphic(editField);
            setContextMenu(null);
        } else {
            setText(item.label());
            setGraphic(null);
            setContextMenu(menuFor(item));
        }
    }

    private boolean isEditableNode(SourceTreeNode node) {
        return node instanceof GenreNode || node instanceof CustomFieldNode || node instanceof TitleNode;
    }

    private String currentRawValue(SourceTreeNode node) {
        return switch (node) {
            case GenreNode g -> g.source().genre() == null ? "" : g.source().genre();
            case CustomFieldNode c -> c.field().value() == null ? "" : c.field().value();
            case TitleNode t -> t.source().title() == null ? "" : t.source().title();
            default -> "";
        };
    }

    private SourceTreeNode buildEditedNode(String newText) {
        return switch (getItem()) {
            case GenreNode g -> new GenreNode(withGenre(g.source(), newText));
            case CustomFieldNode c -> new CustomFieldNode(
                    c.source(),
                    new CustomFieldValue(c.field().fieldDefinitionId(), c.field().fieldName(), newText));
            case TitleNode t -> new TitleNode(withTitle(t.source(), newText));
            default -> getItem();
        };
    }

    private Source withGenre(Source source, String newGenre) {
        return new Source(source.id(), source.projectIds(), source.title(), source.author(), newGenre, source.customFields());
    }

    private Source withTitle(Source source, String newTitle) {
        return new Source(source.id(), source.projectIds(), newTitle, source.author(), source.genre(), source.customFields());
    }
}