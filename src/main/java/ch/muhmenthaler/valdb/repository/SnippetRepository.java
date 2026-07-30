package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.Snippet;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class SnippetRepository {

    public int insert(List<Integer> projectIds, Integer sourceId, String original, String translation,
                      Integer verseStart, Integer verseEnd, Integer page) throws SQLException {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new IllegalArgumentException("A snippet must belong to at least one project");
        }
        Connection conn = Database.get();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int snippetId;
            String sql = "INSERT INTO snippets (source_id, original_content, translation_content, verse_start, verse_end, page) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (sourceId != null) ps.setInt(1, sourceId); else ps.setNull(1, Types.INTEGER);
                ps.setString(2, original);
                ps.setString(3, translation);
                if (verseStart != null) ps.setInt(4, verseStart); else ps.setNull(4, Types.INTEGER);
                if (verseEnd != null) ps.setInt(5, verseEnd); else ps.setNull(5, Types.INTEGER);
                if (page != null) ps.setInt(6, page); else ps.setNull(6, Types.INTEGER);
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

    /** Caller is responsible for not removing a snippet's last remaining project link. */
    public void unlinkProject(int snippetId, int projectId) throws SQLException {
        String sql = "DELETE FROM snippet_projects WHERE snippet_id = ? AND project_id = ?";
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

    /** Full-text search across original + translation content, ranked by relevance. */
    public List<Integer> search(String query) throws SQLException {
        String sql = "SELECT rowid FROM snippets_fts WHERE snippets_fts MATCH ? ORDER BY rank";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, query);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
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
        Map<Integer, Integer> pages = new HashMap<>();

        String snippetSql = "SELECT id, source_id, original_content, translation_content, verse_start, verse_end, page " +
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

                    int page = rs.getInt("page");
                    if (!rs.wasNull()) pages.put(id, page);
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
                    pages.get(id),
                    chaptersBySnippet.get(id),
                    tagsBySnippet.get(id),
                    fieldsBySnippet.get(id)
            ));
        }
        return snippets;
    }

    public Optional<Snippet> loadById(int id) throws SQLException {
        List<Snippet> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}