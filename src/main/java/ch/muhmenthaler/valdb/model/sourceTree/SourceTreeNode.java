package ch.muhmenthaler.valdb.model.sourceTree;

public sealed interface SourceTreeNode permits AuthorNode, TitleNode, GenreNode, CustomFieldNode {
    String label();
}

