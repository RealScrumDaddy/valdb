package ch.muhmenthaler.valdb.model;

import java.util.List;
import java.util.Map;

public record Snippet(
        int id,
        List<Integer> projectIds, // must contain at least one project id
        Source source,            // null if not linked to a source
        String originalContent,
        String translationContent,
        Integer verseStart,       // null if not applicable
        Integer verseEnd,         // null unless the citation is a range, e.g. 57-65
        Integer page,             // null if not applicable
        List<Integer> chapterIds,
        List<Integer> tagIds,
        Map<String, String> customFields
) {}