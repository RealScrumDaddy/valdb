package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.Project;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class ProjectRepository {

    public record DeletePreview(List<Integer> orphanedSourceIds, List<Integer> orphanedSnippetIds) {
        public int sourceCount() { return orphanedSourceIds.size(); }
        public int snippetCount() { return orphanedSnippetIds.size(); }
    }

    public int insert(String name, String description) throws SQLException {
        String sql = "INSERT INTO projects (name, description) VALUES (?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void update(int id, String name, String description) throws SQLException {
        String sql = "UPDATE projects SET name = ?, description = ? WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Reports which sources and snippets would be permanently deleted (not just
     * unlinked) if this project were deleted, without deleting anything.
     */
    public DeletePreview previewDelete(int projectId) throws SQLException {
        Connection conn = Database.get();
        List<Integer> orphanedSnippets = findOrphanedSnippets(conn, projectId);
        List<Integer> orphanedSources = findOrphanedSources(conn, projectId, orphanedSnippets);
        return new DeletePreview(orphanedSources, orphanedSnippets);
    }

    /**
     * Deletes a project. Sources and snippets that belong ONLY to this project (and,
     * for sources, are not still referenced by any surviving snippet) are deleted
     * outright. Sources/snippets that also belong to other projects are kept — they
     * just lose their link to this project.
     */
    public void delete(int projectId) throws SQLException {
        Connection conn = Database.get();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            List<Integer> orphanedSnippets = findOrphanedSnippets(conn, projectId);
            List<Integer> orphanedSources = findOrphanedSources(conn, projectId, orphanedSnippets);

            deleteByIds(conn, "snippets", orphanedSnippets);
            deleteByIds(conn, "sources", orphanedSources);

            // ON DELETE CASCADE handles chapters, tags, field_definitions,
            // and any remaining source_projects/snippet_projects links for
            // sources/snippets that still belong to other projects.
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /** Snippets linked ONLY to projectId (no other project link). */
    private List<Integer> findOrphanedSnippets(Connection conn, int projectId) throws SQLException {
        String sql = """
            SELECT sp.snippet_id FROM snippet_projects sp
            WHERE sp.project_id = ?
            AND NOT EXISTS (
                SELECT 1 FROM snippet_projects sp2
                WHERE sp2.snippet_id = sp.snippet_id AND sp2.project_id != ?
            )
            """;
        List<Integer> orphaned = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setInt(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) orphaned.add(rs.getInt(1));
            }
        }
        return orphaned;
    }

    /**
     * Sources linked ONLY to projectId, and not cited by any snippet that will
     * survive the deletion. doomedSnippetIds must be the result of
     * findOrphanedSnippets for the same projectId, so citations from snippets
     * that are about to be deleted anyway don't falsely save the source.
     */
    private List<Integer> findOrphanedSources(Connection conn, int projectId, List<Integer> doomedSnippetIds) throws SQLException {
        String excludeDoomedClause = doomedSnippetIds.isEmpty() ? "" :
                " AND s.id NOT IN (" + String.join(",", Collections.nCopies(doomedSnippetIds.size(), "?")) + ")";

        String sql = """
            SELECT sp.source_id FROM source_projects sp
            WHERE sp.project_id = ?
            AND NOT EXISTS (
                SELECT 1 FROM source_projects sp2
                WHERE sp2.source_id = sp.source_id AND sp2.project_id != ?
            )
            AND NOT EXISTS (
                SELECT 1 FROM snippets s WHERE s.source_id = sp.source_id
                """ + excludeDoomedClause + """
                )
            """;

        List<Integer> orphaned = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setInt(idx++, projectId);
            ps.setInt(idx++, projectId);
            for (int snippetId : doomedSnippetIds) ps.setInt(idx++, snippetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) orphaned.add(rs.getInt(1));
            }
        }
        return orphaned;
    }

    private void deleteByIds(Connection conn, String table, List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE id IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            ps.executeUpdate();
        }
    }

    public List<Integer> listAll() throws SQLException {
        try (Statement st = Database.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM projects ORDER BY name")) {
            List<Integer> ids = new ArrayList<>();
            while (rs.next()) ids.add(rs.getInt(1));
            return ids;
        }
    }

    public Optional<Project> loadById(int id) throws SQLException {
        List<Project> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Project> loadByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, name, description FROM projects WHERE id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Project> projects = new ArrayList<>();
                while (rs.next()) {
                    projects.add(new Project(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
                }
                return projects;
            }
        }
    }
}