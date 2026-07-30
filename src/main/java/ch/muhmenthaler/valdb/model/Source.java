package ch.muhmenthaler.valdb.model;

import java.util.List;
import java.util.Map;

public record Source(
        int id,
        List<Integer> projectIds, // must contain at least one project id
        String title,
        String author,            // null if unknown
        String genre,             // null if unknown
        Map<String, String> customFields
) {}