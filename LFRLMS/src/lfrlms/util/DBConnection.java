package lfrlms.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Purpose: Loads local database settings and opens a new connection per call.
 * Concept: Static methods, Properties and JDBC resource ownership.
 * Layer: Utility.
 * Rubric: static/final, Exception handling.
 */
public final class DBConnection {
    private DBConnection() {
    }

    // Static factory: every call returns a new connection that the caller must close,
    // so no connection is ever shared between threads
    public static Connection getConnection() throws SQLException, IOException {
        Properties properties = loadProperties();
        String url = properties.getProperty(Constants.DB_URL_KEY, "").trim();
        String username = properties.getProperty(Constants.DB_USERNAME_KEY, "").trim();
        String password = properties.getProperty(Constants.DB_PASSWORD_KEY, "");

        if (url.isEmpty() || username.isEmpty()) {
            throw new IOException("Database URL and username must be set in config/db.properties.");
        }

        try {
            // Explicit loading registers Connector/J with DriverManager in IDE and CLI runs.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException(
                    "MySQL Connector/J is missing from the lib folder.", exception);
        }
        return DriverManager.getConnection(url, username, password);
    }

    private static Properties loadProperties() throws IOException {
        Path propertiesPath = findPropertiesPath();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
        }
        return properties;
    }

    private static Path findPropertiesPath() throws IOException {
        Path currentDirectory = Paths.get("").toAbsolutePath().normalize();
        Path directPath = currentDirectory.resolve("config").resolve("db.properties");
        if (Files.isRegularFile(directPath)) {
            return directPath;
        }

        Path nestedPath = currentDirectory.resolve("LFRLMS")
                .resolve("config").resolve("db.properties");
        if (Files.isRegularFile(nestedPath)) {
            return nestedPath;
        }

        Path parentDirectory = currentDirectory.getParent();
        if (parentDirectory != null) {
            Path parentPath = parentDirectory.resolve("config").resolve("db.properties");
            if (Files.isRegularFile(parentPath)) {
                return parentPath;
            }
        }
        throw new IOException(
                "Cannot find config/db.properties. Run from the LFRLMS project folder.");
    }
}
