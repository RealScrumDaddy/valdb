package ch.muhmenthaler.valdb.model.tree;

public sealed interface SourceTreeNode permits AuthorNode, TitleNode, GenreNode, CustomFieldNode {
    String label();
}

