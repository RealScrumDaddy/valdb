package ch.muhmenthaler.valdb.model.sourceTree;

public record AuthorNode(String authorName) implements SourceTreeNode {
    public String label() { return authorName; }
}
