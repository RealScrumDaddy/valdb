package ch.muhmenthaler.valdb.model;

public record FieldDefinition(int id, int projectId, String entityType, String name, int sortOrder) {}