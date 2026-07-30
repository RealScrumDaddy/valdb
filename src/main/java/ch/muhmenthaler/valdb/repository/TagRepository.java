package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.Tag;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class TagRepository {

    public int insert(int projectId, String name, String color) throws SQLException {
        String sql = "INSERT INTO tags (project_id, name, color) VALUES (?, ?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, name);
            ps.setString(3, color);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void update(int tagId, String name, String color) throws SQLException {
        String sql = "UPDATE tags SET name = ?, color = ? WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, color);
            ps.setInt(3, tagId);
            ps.executeUpdate();
        }
    }

    public void delete(int tagId) throws SQLException {
        // ON DELETE CASCADE cleans up snippet_tags rows automatically
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM tags WHERE id = ?")) {
            ps.setInt(1, tagId);
            ps.executeUpdate();
        }
    }

    public List<Integer> listByProject(int projectId) throws SQLException {
        String sql = "SELECT id FROM tags WHERE project_id = ? ORDER BY name";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    public Optional<Tag> loadById(int id) throws SQLException {
        List<Tag> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Tag> loadByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, project_id, name, color FROM tags WHERE id IN (" + placeholders + ")";

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Tag> tags = new ArrayList<>();
                while (rs.next()) {
                    tags.add(new Tag(
                            rs.getInt("id"), rs.getInt("project_id"),
                            rs.getString("name"), rs.getString("color")
                    ));
                }
                return tags;
            }
        }
    }
}