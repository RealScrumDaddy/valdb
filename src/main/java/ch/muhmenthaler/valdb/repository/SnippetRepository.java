package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.Snippet;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class SnippetRepository {

    public int insert(List<Integer> projectIds, Integer sourceId, String original, String translation,
                      Integer verseStart, Integer verseEnd, Integer pageStart, Integer pageEnd, String notes) throws SQLException {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new IllegalArgumentException("A snippet must belong to at least one project");
        }
        Connection conn = Database.get();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int snippetId;
            String sql = "INSERT INTO snippets (source_id, original_content, translation_content, verse_start, verse_end, page_start, page_end, notes_content) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (sourceId != null) ps.setInt(1, sourceId); else ps.setNull(1, Types.INTEGER);
                ps.setString(2, original);
                ps.setString(3, translation);
                if (verseStart != null) ps.setInt(4, verseStart); else ps.setNull(4, Types.INTEGER);
                if (verseEnd != null) ps.setInt(5, verseEnd); else ps.setNull(5, Types.INTEGER);
                if (pageStart != null) ps.setInt(6, pageStart); else ps.setNull(6, Types.INTEGER);
                if (pageEnd != null) ps.setInt(7, pageEnd); else ps.setNull(7, Types.INTEGER);
                ps.setString(8, notes);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    snippetId = keys.getInt(1);
                }
            }
            String linkSql = "INSERT OR IGNORE INTO snippet_projects (snippet_id, project_id) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                for (int projectId : projectIds) {
                    ps.setInt(1, snippetId);
                    ps.setInt(2, projectId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return snippetId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    public void linkProject(int snippetId, int projectId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO snippet_projects (snippet_id, project_id) VALUES (?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, snippetId);
            ps.setInt(2, projectId);
            ps.executeUpdate();
        }
    }

    public void linkChapter(int snippetId, int chapterId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO snippet_chapters (snippet_id, chapter_id) VALUES (?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, snippetId);
            ps.setInt(2, chapterId);
            ps.executeUpdate();
        }
    }

    public void linkTag(int snippetId, int tagId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO snippet_tags (snippet_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, snippetId);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        }
    }

    /** Distinct ids of sources with at least one snippet linked to the given chapter. */
    public List<Integer> listSourceIdsByChapter(int chapterId) throws SQLException {
        String sql = "SELECT DISTINCT s.source_id FROM snippets s " +
                "JOIN snippet_chapters sc ON sc.snippet_id = s.id " +
                "WHERE sc.chapter_id = ? AND s.source_id IS NOT NULL";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    public void setFieldValue(int snippetId, int fieldDefinitionId, String value) throws SQLException {
        String sql = """
            INSERT INTO snippet_field_values (snippet_id, field_definition_id, value)
            VALUES (?, ?, ?)
            ON CONFLICT(snippet_id, field_definition_id) DO UPDATE SET value = excluded.value
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, snippetId);
            ps.setInt(2, fieldDefinitionId);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    public Map<String, String> getFieldValues(int snippetId) throws SQLException {
        String sql = """
            SELECT fd.name, v.value
            FROM snippet_field_values v
            JOIN field_definitions fd ON fd.id = v.field_definition_id
            WHERE v.snippet_id = ?
            ORDER BY fd.sort_order
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, snippetId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, String> values = new LinkedHashMap<>();
                while (rs.next()) values.put(rs.getString(1), rs.getString(2));
                return values;
            }
        }
    }

    /** Full-text search within one project, optionally limited to one chapter, ranked by relevance. */
    public List<Integer> search(int projectId, Integer chapterId, String userText) throws SQLException {
        String ftsQuery = toFtsQuery(userText);
        if (ftsQuery == null) return List.of();
        StringBuilder sql = new StringBuilder("""
            SELECT snippets_fts.rowid FROM snippets_fts
            WHERE snippets_fts MATCH ?
              AND EXISTS (SELECT 1 FROM snippet_projects sp
                          WHERE sp.snippet_id = snippets_fts.rowid AND sp.project_id = ?)
            """);
        if (chapterId != null) {
            sql.append("""
                  AND EXISTS (SELECT 1 FROM snippet_chapters sc
                              WHERE sc.snippet_id = snippets_fts.rowid AND sc.chapter_id = ?)
                """);
        }
        sql.append("ORDER BY rank");

        try (PreparedStatement ps = Database.get().prepareStatement(sql.toString())) {
            ps.setString(1, ftsQuery);
            ps.setInt(2, projectId);
            if (chapterId != null) ps.setInt(3, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    /**
     * Turns free text into a safe FTS5 query: every word is quoted, so FTS5 operators typed by the user
     * (-, :, *, AND, quotes, …) are treated as plain text. The last word matches as a prefix.
     * Returns null if nothing searchable is left.
     */
    private static String toFtsQuery(String userText) {
        if (userText == null) return null;
        List<String> terms = Arrays.stream(userText.trim().split("\\s+"))
                .filter(t -> t.codePoints().anyMatch(Character::isLetterOrDigit))
                .map(t -> "\"" + t.replace("\"", "\"\"") + "\"")
                .toList();
        if (terms.isEmpty()) return null;
        return String.join(" ", terms) + "*";
    }

    /** Any of projectId, chapterId, tagId may be null to skip that condition. */
    public List<Integer> filter(Integer projectId, Integer chapterId, Integer tagId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT s.id FROM snippets s WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (projectId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM snippet_projects sp WHERE sp.snippet_id = s.id AND sp.project_id = ?)");
            params.add(projectId);
        }
        if (chapterId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM snippet_chapters sc WHERE sc.snippet_id = s.id AND sc.chapter_id = ?)");
            params.add(chapterId);
        }
        if (tagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM snippet_tags st WHERE st.snippet_id = s.id AND st.tag_id = ?)");
            params.add(tagId);
        }

        try (PreparedStatement ps = Database.get().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    public List<Snippet> loadByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));

        // 1. Base rows
        Map<Integer, String> originals = new LinkedHashMap<>();
        Map<Integer, String> translations = new HashMap<>();
        Map<Integer, Integer> sourceIds = new HashMap<>();
        Map<Integer, Integer> verseStarts = new HashMap<>();
        Map<Integer, Integer> verseEnds = new HashMap<>();
        Map<Integer, Integer> pageStarts = new HashMap<>();
        Map<Integer, Integer> pageEnds = new HashMap<>();
        Map<Integer, String> notes = new HashMap<>();

        String snippetSql = "SELECT id, source_id, original_content, translation_content, verse_start, verse_end, page_start, page_end, notes_content " +
                "FROM snippets WHERE id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(snippetSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    originals.put(id, rs.getString("original_content"));
                    translations.put(id, rs.getString("translation_content"));

                    int sourceId = rs.getInt("source_id");
                    if (!rs.wasNull()) sourceIds.put(id, sourceId);

                    int verseStart = rs.getInt("verse_start");
                    if (!rs.wasNull()) verseStarts.put(id, verseStart);

                    int verseEnd = rs.getInt("verse_end");
                    if (!rs.wasNull()) verseEnds.put(id, verseEnd);

                    int pageStart = rs.getInt("page_start");
                    if (!rs.wasNull()) pageStarts.put(id, pageStart);

                    int pageEnd = rs.getInt("page_end");
                    if (!rs.wasNull()) pageEnds.put(id, pageEnd);

                    notes.put(id, rs.getString("notes_content"));
                }
            }
        }

        // 2. Project links, batched
        Map<Integer, List<Integer>> projectsBySnippet = new HashMap<>();
        for (Integer id : originals.keySet()) projectsBySnippet.put(id, new ArrayList<>());

        String projectSql = "SELECT snippet_id, project_id FROM snippet_projects WHERE snippet_id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(projectSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    projectsBySnippet.get(rs.getInt("snippet_id")).add(rs.getInt("project_id"));
                }
            }
        }

        // 3. Custom field values, batched
        Map<Integer, Map<String, String>> fieldsBySnippet = new HashMap<>();
        for (Integer id : originals.keySet()) fieldsBySnippet.put(id, new LinkedHashMap<>());

        String fieldSql = "SELECT v.snippet_id, fd.name, v.value FROM snippet_field_values v " +
                "JOIN field_definitions fd ON fd.id = v.field_definition_id " +
                "WHERE v.snippet_id IN (" + placeholders + ") ORDER BY fd.sort_order";
        try (PreparedStatement ps = Database.get().prepareStatement(fieldSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fieldsBySnippet.get(rs.getInt("snippet_id"))
                            .put(rs.getString("name"), rs.getString("value"));
                }
            }
        }

        // 4. Chapter links, batched
        Map<Integer, List<Integer>> chaptersBySnippet = new HashMap<>();
        for (Integer id : originals.keySet()) chaptersBySnippet.put(id, new ArrayList<>());

        String chapterSql = "SELECT snippet_id, chapter_id FROM snippet_chapters WHERE snippet_id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(chapterSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chaptersBySnippet.get(rs.getInt("snippet_id")).add(rs.getInt("chapter_id"));
                }
            }
        }

        // 5. Tag links, batched
        Map<Integer, List<Integer>> tagsBySnippet = new HashMap<>();
        for (Integer id : originals.keySet()) tagsBySnippet.put(id, new ArrayList<>());

        String tagSql = "SELECT snippet_id, tag_id FROM snippet_tags WHERE snippet_id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(tagSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tagsBySnippet.get(rs.getInt("snippet_id")).add(rs.getInt("tag_id"));
                }
            }
        }

        // 6. Sources, batched via SourceRepository (dedup source ids first)
        List<Integer> distinctSourceIds = sourceIds.values().stream().distinct().toList();
        Map<Integer, Source> sourcesById = new HashMap<>();
        if (!distinctSourceIds.isEmpty()) {
            for (Source source : new SourceRepository().loadByIds(distinctSourceIds)) {
                sourcesById.put(source.id(), source);
            }
        }

        // 7. Stitch it all together
        List<Snippet> snippets = new ArrayList<>();
        for (var entry : originals.entrySet()) {
            int id = entry.getKey();
            Integer sourceId = sourceIds.get(id);
            Source source = sourceId != null ? sourcesById.get(sourceId) : null;

            snippets.add(new Snippet(
                    id,
                    projectsBySnippet.get(id),
                    source,
                    entry.getValue(),
                    translations.get(id),
                    verseStarts.get(id),
                    verseEnds.get(id),
                    pageStarts.get(id),
                    pageEnds.get(id),
                    notes.get(id),
                    chaptersBySnippet.get(id),
                    tagsBySnippet.get(id),
                    fieldsBySnippet.get(id)
            ));
        }
        Map<Integer, Integer> position = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) position.putIfAbsent(ids.get(i), i);
        snippets.sort(Comparator.comparingInt(s -> position.get(s.id())));
        return snippets;
    }

    public Optional<Snippet> loadById(int id) throws SQLException {
        List<Snippet> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void update(int snippetId, Integer sourceId, String original, String translation,
                       Integer verseStart, Integer verseEnd, Integer pageStart, Integer pageEnd, String notes) throws SQLException {
        String sql = "UPDATE snippets SET source_id = ?, original_content = ?, translation_content = ?, "
                + "verse_start = ?, verse_end = ?, page_start = ?, page_end = ?, notes_content = ? WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            setNullableInt(ps, 1, sourceId);
            ps.setString(2, original);
            ps.setString(3, translation);
            setNullableInt(ps, 4, verseStart);
            setNullableInt(ps, 5, verseEnd);
            setNullableInt(ps, 6, pageStart);
            setNullableInt(ps, 7, pageEnd);
            ps.setString(8, notes);
            ps.setInt(9, snippetId);
            ps.executeUpdate();
        }
    }

    public void setChapters(int snippetId, Collection<Integer> chapterIds) throws SQLException {
        replaceLinks("snippet_chapters", "chapter_id", snippetId, chapterIds);
    }

    public void setTags(int snippetId, Collection<Integer> tagIds) throws SQLException {
        replaceLinks("snippet_tags", "tag_id", snippetId, tagIds);
    }

    public void delete(int snippetId) throws SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM snippets WHERE id = ?")) {
            ps.setInt(1, snippetId);
            ps.executeUpdate();
        }
    }

    /**
     * Removes the snippet from the project, along with its chapters, tags and field values from that project.
     * Refuses to remove the snippet's last project; use delete() for that.
     */
    public void unlinkProject(int snippetId, int projectId) throws SQLException {
        Connection conn = Database.get();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            executeUpdate(conn, "DELETE FROM snippet_chapters WHERE snippet_id = ? "
                    + "AND chapter_id IN (SELECT id FROM chapters WHERE project_id = ?)", snippetId, projectId);
            executeUpdate(conn, "DELETE FROM snippet_tags WHERE snippet_id = ? "
                    + "AND tag_id IN (SELECT id FROM tags WHERE project_id = ?)", snippetId, projectId);
            executeUpdate(conn, "DELETE FROM snippet_field_values WHERE snippet_id = ? "
                    + "AND field_definition_id IN (SELECT id FROM field_definitions WHERE project_id = ?)", snippetId, projectId);
            executeUpdate(conn, "DELETE FROM snippet_projects WHERE snippet_id = ? AND project_id = ?", snippetId, projectId);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM snippet_projects WHERE snippet_id = ?)")) {
                ps.setInt(1, snippetId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (!rs.getBoolean(1)) {
                        throw new SQLException("Snippet " + snippetId + " would be left without a project");
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static void executeUpdate(Connection conn, String sql, int... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setInt(i + 1, params[i]);
            ps.executeUpdate();
        }
    }

    private void replaceLinks(String table, String column, int snippetId, Collection<Integer> ids) throws SQLException {
        Set<Integer> wanted = new LinkedHashSet<>(ids);

        String deleteSql = "DELETE FROM " + table + " WHERE snippet_id = ?";
        if (!wanted.isEmpty()) {
            deleteSql += " AND " + column + " NOT IN (" + String.join(",", Collections.nCopies(wanted.size(), "?")) + ")";
        }
        try (PreparedStatement ps = Database.get().prepareStatement(deleteSql)) {
            ps.setInt(1, snippetId);
            int index = 2;
            for (int id : wanted) ps.setInt(index++, id);
            ps.executeUpdate();
        }

        String insertSql = "INSERT OR IGNORE INTO " + table + " (snippet_id, " + column + ") VALUES (?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(insertSql)) {
            for (int id : wanted) {
                ps.setInt(1, snippetId);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}