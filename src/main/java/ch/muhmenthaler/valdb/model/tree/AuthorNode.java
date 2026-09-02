package ch.muhmenthaler.valdb.model.tree;

public record AuthorNode(String authorName) implements SourceTreeNode {
    public String label() { return authorName; }
}
