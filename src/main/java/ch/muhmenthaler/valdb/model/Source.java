package ch.muhmenthaler.valdb.model;

import java.util.List;

public record Source(
        int id,
        List<Integer> projectIds, // must contain at least one project id
        String title,
        String author,            // null if unknown
        String genre,             // null if unknown
        List<CustomFieldValue> customFields
) {}