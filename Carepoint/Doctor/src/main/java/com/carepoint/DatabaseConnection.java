package com.carepoint;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseConnection {
    private DatabaseConnection() {}

    public static Connection connect() throws SQLException {
        Configuration configuration = readConfiguration();
        List<String> missing = configuration.missingVariables();
        if (!missing.isEmpty()) {
            throw new SQLException("Database configuration is missing: " + String.join(", ", missing)
                    + ". Set the missing variable(s) in the environment of the process launching CarePoint.");
        }
        return DriverManager.getConnection(configuration.url(), configuration.user(), configuration.password());
    }

    private static Configuration readConfiguration() {
        String url = trimEnv("DB_URL");
        String user = trimEnv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        return new Configuration(url, user, password);
    }

    private record Configuration(String url, String user, String password) {
        private List<String> missingVariables() {
            List<String> missing = new ArrayList<>();
            if (url == null || url.isBlank()) missing.add("DB_URL");
            if (user == null || user.isBlank()) missing.add("DB_USER");
            if (password == null || password.isBlank()) missing.add("DB_PASSWORD");
            return missing;
        }
    }

    static String configurationStatus() {
        List<String> missing = readConfiguration().missingVariables();
        return missing.isEmpty() ? "DB_URL, DB_USER, and DB_PASSWORD are present."
                : "Missing database environment variables: " + String.join(", ", missing) + ".";
    }

    static String redactDatabaseValues(String detail) {
        if (detail == null) return null;
        for (String key : new String[]{"DB_PASSWORD", "DB_USER", "DB_URL"}) {
            String value = System.getenv(key);
            if (value != null && !value.isEmpty()) detail = detail.replace(value, "[redacted]");
        }
        return detail;
    }

    private static String trimEnv(String key) {
        String value = System.getenv(key);
        return value == null ? null : value.trim();
    }

    public static void testConnection() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("MySQL connection opened, but the SELECT 1 verification failed.");
            }
            System.out.printf("Connected to %s %s (database: %s). SELECT 1 passed.%n",
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getDatabaseProductVersion(),
                    connection.getCatalog());
        }
    }

    public static void main(String[] args) {
        try {
            testConnection();
        } catch (SQLException exception) {
            System.err.println("Database connection test failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
