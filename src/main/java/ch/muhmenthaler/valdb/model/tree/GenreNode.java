package ch.muhmenthaler.valdb.model.tree;

import ch.muhmenthaler.valdb.model.Source;

public record GenreNode(Source source) implements SourceTreeNode {
    public String label() {
        String genre = source.genre();
        return "Genre: " + (genre == null || genre.isBlank() ? "(none)" : genre);
    }
}
