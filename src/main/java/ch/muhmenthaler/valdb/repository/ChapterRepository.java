package ch.muhmenthaler.valdb.repository;

import ch.muhmenthaler.valdb.model.Chapter;
import ch.muhmenthaler.valdb.model.db.Database;

import java.sql.*;
import java.util.*;

public class ChapterRepository {

    public int insert(int projectId, String title, int sortOrder) throws SQLException {
        String sql = "INSERT INTO chapters (project_id, title, sort_order) VALUES (?, ?, ?)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, title);
            ps.setInt(3, sortOrder);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void rename(int chapterId, String title) throws SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement("UPDATE chapters SET title = ? WHERE id = ?")) {
            ps.setString(1, title);
            ps.setInt(2, chapterId);
            ps.executeUpdate();
        }
    }

    public void reorder(int chapterId, int sortOrder) throws SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement("UPDATE chapters SET sort_order = ? WHERE id = ?")) {
            ps.setInt(1, sortOrder);
            ps.setInt(2, chapterId);
            ps.executeUpdate();
        }
    }

    public void delete(int chapterId) throws SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM chapters WHERE id = ?")) {
            ps.setInt(1, chapterId);
            ps.executeUpdate();
        }
    }

    public List<Integer> listByProject(int projectId) throws SQLException {
        String sql = "SELECT id FROM chapters WHERE project_id = ? ORDER BY sort_order";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getInt(1));
                return ids;
            }
        }
    }

    public Optional<Chapter> loadById(int id) throws SQLException {
        List<Chapter> results = loadByIds(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Chapter> loadByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, project_id, title, sort_order FROM chapters WHERE id IN (" + placeholders + ")";

        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Chapter> chapters = new ArrayList<>();
                while (rs.next()) {
                    chapters.add(new Chapter(
                            rs.getInt("id"), rs.getInt("project_id"),
                            rs.getString("title"), rs.getInt("sort_order")
                    ));
                }
                return chapters;
            }
        }
    }
}
