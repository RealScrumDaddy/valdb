package ch.muhmenthaler.valdb.business;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("ch.muhmenthaler.valdb");

    static {
        try {
            Path logDir = resolveLogDir();
            Files.createDirectories(logDir);

            FileHandler fileHandler = new FileHandler(logDir.resolve("valdb.log").toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    private static Path resolveLogDir() {
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
        return baseDir;
    }


    public static Logger get() {
        return LOGGER;
    }
}

