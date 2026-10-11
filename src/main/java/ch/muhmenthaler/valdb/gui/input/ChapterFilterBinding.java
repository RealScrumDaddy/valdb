package ch.muhmenthaler.valdb.gui.input;

import ch.muhmenthaler.valdb.model.Chapter;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

public class ChapterFilterBinding {

    private ComboBox<Chapter> comboBox;

    public ChapterFilterBinding(ComboBox<Chapter> comboBox, Runnable onSelectionChange){
       this.comboBox = comboBox;
       comboBox.setConverter(new StringConverter<>() {
            @Override public String toString(Chapter chapter) {
                return chapter == null ? "All chapters" : chapter.title();
            }
            @Override public Chapter fromString(String string) {
                return null;
            }
        });
        comboBox.valueProperty().addListener((obs, oldChapter, newChapter) -> onSelectionChange.run());
    }

    public void setChapters(List<Chapter> chapters) {
        List<Chapter> withAllOption = new ArrayList<>();
        withAllOption.add(null);
        withAllOption.addAll(chapters);
        comboBox.getItems().setAll(withAllOption);
        comboBox.setValue(null);
    }

    public Chapter getSelectedChapter() {
        return comboBox.getValue();
    }
}
