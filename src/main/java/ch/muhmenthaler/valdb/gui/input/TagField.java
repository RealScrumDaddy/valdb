package ch.muhmenthaler.valdb.gui.input;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.util.*;

public class TagField extends FlowPane{

    private TextField textField;
    private Set<String> tagSet = new HashSet<>();

    public TagField(TextField textField, String promptText){
        this.textField = textField;
        getStyleClass().add("tag-field");

        textField.getStyleClass().add("tag-input");
        textField.setPromptText(promptText);
        textField.setPrefColumnCount(10);

        textField.setOnAction(e -> {
            commitPendingTag();
        });


        textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue){
                commitPendingTag();
            }
        });

        setOnMouseClicked(e -> textField.requestFocus());

        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.BACK_SPACE && textField.getText().isEmpty() && !tagSet.isEmpty()) {
                removeLastTag();
                e.consume();
            }
        });

        getChildren().add(textField);
    }

    private void commitPendingTag(){
        String text = this.textField.getText().trim();
        if (!text.isBlank()){
            addTag(text);
            textField.clear();
        }
    }

    private void addTag(String text) {
        if (tagSet.contains(text)) return;
        HBox tag = TagPill.create(text, null, pill -> {
            getChildren().remove(pill);
            tagSet.remove(text);
        });
        getChildren().add(getChildren().indexOf(textField), tag);
        tagSet.add(text);
    }

    public void setTags(Collection<String> tags) {
        getChildren().removeIf(node -> node != textField);
        tagSet.clear();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) addTag(tag.trim());
        }
    }

    public List<String> getTagSet(){
        return new ArrayList<>(this.tagSet);
    }

    private void removeLastTag(){
        int textFieldIndex = getChildren().indexOf(textField);
        if (textFieldIndex == 0) return;
        javafx.scene.Node lastPill = getChildren().get(textFieldIndex - 1);
        getChildren().remove(lastPill);
        String tagText = (String) lastPill.getUserData();
        tagSet.remove(tagText);
    }
}