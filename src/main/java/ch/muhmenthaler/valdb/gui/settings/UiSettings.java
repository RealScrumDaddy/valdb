package ch.muhmenthaler.valdb.gui.settings;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;

import java.util.prefs.Preferences;

/** App-wide UI settings, persisted between launches. */
public final class UiSettings {

    public static final int DEFAULT_FONT_SIZE = 13;
    public static final int MIN_FONT_SIZE = 9;
    public static final int MAX_FONT_SIZE = 28;

    private static final String FONT_SIZE_KEY = "contentFontSize";
    private static final UiSettings INSTANCE = new UiSettings();

    private final Preferences prefs = Preferences.userNodeForPackage(UiSettings.class);
    private final IntegerProperty fontSize;
    private final StringExpression fontSizeStyle;

    private UiSettings() {
        int stored = Math.clamp(prefs.getInt(FONT_SIZE_KEY, DEFAULT_FONT_SIZE), MIN_FONT_SIZE, MAX_FONT_SIZE);
        fontSize = new SimpleIntegerProperty(stored);
        fontSize.addListener((obs, oldV, newV) -> prefs.putInt(FONT_SIZE_KEY, newV.intValue()));
        fontSizeStyle = Bindings.format("-fx-font-size: %dpx;", fontSize);
    }

    public static UiSettings get() { return INSTANCE; }

    public IntegerProperty fontSizeProperty() { return fontSize; }
    public int getFontSize() { return fontSize.get(); }

    public void setFontSize(int size) {
        fontSize.set(Math.clamp(size, MIN_FONT_SIZE, MAX_FONT_SIZE));
    }

    public void changeFontSize(int delta) { setFontSize(getFontSize() + delta); }
    public void resetFontSize() { setFontSize(DEFAULT_FONT_SIZE); }

    /**
     * Makes the node (and everything inside it) follow the font size setting.
     * Binds the node's inline style, so don't call setStyle() on that same node elsewhere.
     */
    public void applyFontSize(Node node) {
        node.styleProperty().bind(fontSizeStyle);
    }
}
