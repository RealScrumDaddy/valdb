package ch.muhmenthaler.valdb.model.db;

import ch.muhmenthaler.valdb.business.AppLogger;
import ch.muhmenthaler.valdb.gui.settings.UiSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.logging.Level;

public class Database {

    private static Connection connection;

    public static synchronized Connection get() {
    if (connection == null) {
        try {
            Path dbPath = resolveDbPath();
            Path backup = UiSettings.get().backupPath();
            if (!Files.exists(dbPath) && backup != null && Files.exists(backup)) {
                Files.createDirectories(dbPath.getParent());
                Files.copy(backup, dbPath);
            }

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
        String appData = System.getenv("LOCALAPPDATA");
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
        int target = 1; // bump this and add a branch below for each future migration

        if (current < 1) runScript("/ch/muhmenthaler/valdb/model/db/migration/V1__init.sql");
        // if (current < 2) runScript("/migrations/V2__something.sql");

        setUserVersion(target);
    }

    public static synchronized void backupTo(Path backupFile) throws SQLException, IOException {
        Files.createDirectories(backupFile.getParent());
        Path tmp = backupFile.resolveSibling(backupFile.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        try (PreparedStatement ps = get().prepareStatement("VACUUM INTO ?")) {
            ps.setString(1, tmp.toString());
            ps.execute();
        }
        // write to .tmp first, so a half-written backup never replaces a good one
        Files.move(tmp, backupFile, StandardCopyOption.REPLACE_EXISTING);
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