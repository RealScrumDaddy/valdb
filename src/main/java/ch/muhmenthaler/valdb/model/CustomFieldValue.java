package ch.muhmenthaler.valdb.model;

public record CustomFieldValue(int fieldDefinitionId, String fieldName, String value) {
}
