package ch.muhmenthaler.valdb.model.tree;

import ch.muhmenthaler.valdb.model.CustomFieldValue;
import ch.muhmenthaler.valdb.model.Source;

public record CustomFieldNode(Source source, CustomFieldValue field) implements SourceTreeNode {
    public String label() {
        String value = field.value();
        return field.fieldName() + ": " + (value == null || value.isBlank() ? "(empty)" : value);
    }
}

