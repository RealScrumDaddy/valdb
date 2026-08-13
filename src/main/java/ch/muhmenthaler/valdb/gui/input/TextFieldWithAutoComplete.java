package ch.muhmenthaler.valdb.gui.input;

import javafx.event.ActionEvent;
import javafx.geometry.Side;
import javafx.scene.control.*;

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TextFieldWithAutoComplete extends TextField {
    private final SortedSet<String> entries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final ContextMenu entriesPopup = new ContextMenu();
    private boolean suppressPopupUpdate = false;

    public TextFieldWithAutoComplete(List<String> entries){
        this.entries.addAll(entries);
        textProperty().addListener(((observable, oldValue, newValue) -> {
            getAndShowRelevantEntries();
        }));
        focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue){
                getAndShowRelevantEntries();
            }else {
                this.entriesPopup.hide();
            }
        });
        this.localToSceneTransformProperty().addListener((obs, oldTransform, newTransform) -> {
            if (this.entriesPopup.isShowing()) {
                this.entriesPopup.hide();
                this.entriesPopup.show(TextFieldWithAutoComplete.this, Side.BOTTOM, 0, 0);
            }
        });
    }

    private void getAndShowRelevantEntries(){
        if (this.suppressPopupUpdate){
            return;
        }
        if (!isFocused()){
            this.entriesPopup.hide();
            return;
        }
        if (this.entries.isEmpty()){
            this.entriesPopup.hide();
            return;
        }
        List<String> searchResult = new LinkedList<>();
        if (getText().isBlank()){
            searchResult.addAll(this.entries);
        }else{
            searchResult.addAll(this.entries.subSet(getText(), getText() + Character.MAX_VALUE));
        }
        if (searchResult.isEmpty()){
            this.entriesPopup.hide();
            return;
        }
        populatePopup(searchResult);
        if (!this.entriesPopup.isShowing()){
            this.entriesPopup.show(TextFieldWithAutoComplete.this, Side.BOTTOM, 0, 0);
        }
    }

    private void populatePopup(List<String> searchResult){
        List<MenuItem> menuItems = new LinkedList<>();
        int maxEntries = 10;
        for (int i = 0; i < Math.min(searchResult.size(), maxEntries); i++) {
            final String result = searchResult.get(i);
            MenuItem item = new MenuItem(result);
            item.setOnAction((ActionEvent e) -> {
                this.suppressPopupUpdate = true;
                setText(result);
                end();
                this.suppressPopupUpdate = false;
                this.entriesPopup.hide();
            });
            menuItems.add(item);
        }
        this.entriesPopup.getItems().clear();
        this.entriesPopup.getItems().addAll(menuItems);
    }
}