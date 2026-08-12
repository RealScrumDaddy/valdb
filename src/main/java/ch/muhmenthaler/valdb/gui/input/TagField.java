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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagField extends FlowPane{

    private TextField textField;
    private Set<String> tagSet = new HashSet<>();

    public TagField(TextField textField, String promptText){
        this.textField = textField;
        getStyleClass().add("tag-field");
        setHgap(6);
        setVgap(6);
        setPadding(new Insets(6));

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

    private void addTag(String text){
        if (this.tagSet.contains(text)) return;

        Label label = new Label(text);
        label.getStyleClass().add("tag-label");

        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("tag-remove-button");

        HBox tag = new HBox(4, label, removeBtn);
        tag.getStyleClass().add("tag-pill");
        tag.setUserData(text);
        tag.setAlignment(Pos.CENTER);
        tag.setPadding(new Insets(4, 8, 4, 8));

        removeBtn.setOnAction(e -> {
            getChildren().remove(tag);
            this.tagSet.remove(text);
        });

        getChildren().add(getChildren().indexOf(textField), tag);
        this.tagSet.add(text);
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