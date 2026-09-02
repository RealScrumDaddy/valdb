package ch.muhmenthaler.valdb.model.tree;

import ch.muhmenthaler.valdb.model.Source;

public record TitleNode(Source source) implements SourceTreeNode {
    public String label() { return source.title(); }
}
