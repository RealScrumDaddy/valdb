package ch.muhmenthaler.valdb.model.db;

import ch.muhmenthaler.valdb.business.AppLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.logging.Level;

public class Database {

    private static Connection connection;

    public static synchronized Connection get() {
    if (connection == null) {
        try {
            Path dbPath = resolveDbPath();
            Files.createDirectories(dbPath.getParent());

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            migrate();
        } catch (SQLException | IOException e) {
            Path dbPath = resolveDbPath();
            AppLogger.get().log(Level.SEVERE, "Failed to open database at " + dbPath, e);
            throw new RuntimeException("Failed to open database at " + dbPath, e);
        }
    }
    return connection;
    }

    private static Path resolveDbPath() {
        String os = System.getProperty("os.name").toLowerCase();
        Path baseDir;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            baseDir = Paths.get(appData, "ValDB");
        } else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            Path dataHome = (xdgData != null && !xdgData.isBlank())
                    ? Paths.get(xdgData)
                    : Paths.get(System.getProperty("user.home"), ".local", "share");
            baseDir = dataHome.resolve("ValDB");
        }

        return baseDir.resolve("valdb.sqlite");
    }

    private static void migrate() throws SQLException {
        int current = getUserVersion();

        if (current < 1) applyMigration(1, "/ch/muhmenthaler/valdb/model/db/migration/V1__init.sql");
        if (current < 2) applyMigration(2, "/ch/muhmenthaler/valdb/model/db/migration/V2__page_range.sql");
        if (current < 3) applyMigration(3, "/ch/muhmenthaler/valdb/model/db/migration/V3__snippet_notes.sql");
        // if (current < 4) applyMigration(4, ".../V4__something.sql");
    }

    private static void applyMigration(int version, String resourcePath) throws SQLException {
        connection.setAutoCommit(false);
        try {
            runScript(resourcePath);
            setUserVersion(version);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static int getUserVersion() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setUserVersion(int version) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA user_version = " + version);
        }
    }

    private static void runScript(String resourcePath) throws SQLException {
        String sql = readResource(resourcePath);
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static String readResource(String path) {
        try (InputStream is = Database.class.getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}