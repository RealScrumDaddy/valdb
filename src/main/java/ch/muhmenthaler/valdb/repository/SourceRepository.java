package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.CustomFieldValue;
import ch.muhmenthaler.valdb.model.Source;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class SourceRepository {

    public int insert(List<Integer> projectIds, String title, String author, String genre) throws SQLException {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new IllegalArgumentException("A source must belong to at least one project");
        }
        Connection conn = Database.get();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int sourceId;
            String sql = "INSERT INTO sources (title, author, genre) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, title);
                ps.setString(2, author);
                ps.setString(3, genre);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    sourceId = keys.getInt(1);
                }
            }
            linkProjectsBatch(conn, sourceId, projectIds);
            conn.commit();
            return sourceId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private void linkProjectsBatch(Connection conn, int sourceId, List<Integer> projectIds) throws SQLException {
        String sql = "INSERT OR IGNORE INTO source_projects (source_id, project_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int projectId : projectIds) {
                ps.setInt(1, sourceId);
                ps.setInt(2, projectId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void linkProject(int sourceId, int projectId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO source_projects (source_id, project_id) VALUES (?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, sourceId);
            ps.setInt(2, projectId);
            ps.executeUpdate();
        }
    }

    /** Caller is responsible for not removing a source's last remaining project link. */
    public void unlinkProject(int sourceId, int projectId) throws SQLException {
        String sql = "DELETE FROM source_projects WHERE source_id = ? AND project_id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, sourceId);
            ps.setInt(2, projectId);
            ps.executeUpdate();
        }
    }

    public Optional<Integer> findIdByTitle(int projectId, String title) throws SQLException {
        String sql = "SELECT s.id FROM sources s " +
                "JOIN source_projects sp ON sp.source_id = s.id " +
                "WHERE sp.project_id = ? AND s.title = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setString(2, title);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }

    public int findOrCreate(int projectId, String title) throws SQLException {
        return findIdByTitle(projectId, title)
                .orElseGet(() -> {
                    try {
                        return insert(List.of(projectId), title, null, null);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public void update(int sourceId, String title, String author, String genre) throws SQLException {
        // Fires the sources_au trigger, which re-derives source_fields_text for every citing snippet
        String sql = "UPDATE sources SET title = ?, author = ?, genre = ? WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, genre);
            ps.setInt(4, sourceId);
            ps.executeUpdate();
        }
    }

    public void delete(int sourceId) throws SQLException {
        // snippets.source_id has ON DELETE SET NULL — citing snippets survive, just lose the link
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM sources WHERE id = ?")) {
            ps.setInt(1, sourceId);
            ps.executeUpdate();
        }
    }

    public void setFieldValue(int sourceId, int fieldDefinitionId, String value) throws SQLException {
        String sql = """
            INSERT INTO source_field_values (source_id, field_definition_id, value)
            VALUES (?, ?, ?)
            ON CONFLICT(source_id, field_definition_id) DO UPDATE SET value = excluded.value
            """;
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, sourceId);
            ps.setInt(2, fieldDefinitionId);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    private Map<Integer, List<CustomFieldValue>> loadFieldValues(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Integer, List<CustomFieldValue>> fieldsBySource = new HashMap<>();
        for (Integer id : ids) fieldsBySource.put(id, new ArrayList<>());
        String fieldSql = "SELECT fd.id, v.source_id, fd.name, v.value FROM source_field_values v " +
                "JOIN field_definitions fd ON fd.id = v.field_definition_id " +
                "WHERE v.source_id IN (" + placeholders + ") ORDER BY fd.sort_order";
        try (PreparedStatement ps = Database.get().prepareStatement(fieldSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fieldsBySource.get(rs.getInt("source_id"))
                            .add(new CustomFieldValue(
                                    rs.getInt("id"),
                                    rs.getString("name"),
                                    rs.getString("value")
                            ));
                }
            }
        }
        return fieldsBySource;
    }

    public List<CustomFieldValue> getFieldValues(int sourceId) throws SQLException {
        return loadFieldValues(List.of(sourceId)).getOrDefault(sourceId, List.of());
    }


    public List<Integer> listByProject(int projectId) throws SQLException {
        String sql = "SELECT s.id FROM sources s " +
                "JOIN source_projects sp ON sp.source_id = s.id " +
                "WHERE sp.project_id = ? ORDER BY s.title";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    public Optional<Source> loadById(int id) throws SQLException {
        List<Source> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Source> loadByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));

        Map<Integer, String> titles = new LinkedHashMap<>();
        Map<Integer, String> authors = new HashMap<>();
        Map<Integer, String> genres = new HashMap<>();

        String sourceSql = "SELECT id, title, author, genre FROM sources WHERE id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(sourceSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    titles.put(id, rs.getString("title"));
                    authors.put(id, rs.getString("author"));
                    genres.put(id, rs.getString("genre"));
                }
            }
        }

        Map<Integer, List<Integer>> projectsBySource = new HashMap<>();
        for (Integer id : titles.keySet()) projectsBySource.put(id, new ArrayList<>());

        String projectSql = "SELECT source_id, project_id FROM source_projects WHERE source_id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(projectSql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    projectsBySource.get(rs.getInt("source_id")).add(rs.getInt("project_id"));
                }
            }
        }

        Map<Integer, List<CustomFieldValue>> fieldsBySource = loadFieldValues(ids);

        List<Source> sources = new ArrayList<>();
        for (var entry : titles.entrySet()) {
            int id = entry.getKey();
            sources.add(new Source(
                    id,
                    projectsBySource.get(id),
                    entry.getValue(),
                    authors.get(id),
                    genres.get(id),
                    fieldsBySource.get(id)
            ));
        }
        return sources;
    }
}