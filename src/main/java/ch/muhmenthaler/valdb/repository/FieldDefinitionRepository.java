package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.FieldDefinition;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class FieldDefinitionRepository {

    public int insert(int projectId, String entityType, String name, int sortOrder) throws SQLException {
        String sql = "INSERT INTO field_definitions (project_id, entity_type, name, sort_order) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, entityType);
            ps.setString(3, name);
            ps.setInt(4, sortOrder);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void rename(int fieldDefinitionId, String name) throws SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement("UPDATE field_definitions SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, fieldDefinitionId);
            ps.executeUpdate();
        }
    }

    public void delete(int fieldDefinitionId) throws SQLException {
        // ON DELETE CASCADE cleans up matching source_field_values / snippet_field_values
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM field_definitions WHERE id = ?")) {
            ps.setInt(1, fieldDefinitionId);
            ps.executeUpdate();
        }
    }

    /** entityType must be "source" or "snippet". */
    public List<Integer> listByProject(int projectId, String entityType) throws SQLException {
        String sql = "SELECT id FROM field_definitions WHERE project_id = ? AND entity_type = ? ORDER BY sort_order";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setString(2, entityType);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    public Optional<FieldDefinition> loadById(int id) throws SQLException {
        List<FieldDefinition> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<FieldDefinition> loadByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, project_id, entity_type, name, sort_order " +
                "FROM field_definitions WHERE id IN (" + placeholders + ")";

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<FieldDefinition> defs = new ArrayList<>();
                while (rs.next()) {
                    defs.add(new FieldDefinition(
                            rs.getInt("id"), rs.getInt("project_id"),
                            rs.getString("entity_type"), rs.getString("name"), rs.getInt("sort_order")
                    ));
                }
                return defs;
            }
        }
    }
}
