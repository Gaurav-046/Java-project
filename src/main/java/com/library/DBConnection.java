package com.library;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/library_db";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "your_password";

    private static final String URL = env("DB_URL", DEFAULT_URL);
    private static final String USER = env("DB_USER", DEFAULT_USER);
    private static final String PASSWORD = env("DB_PASSWORD", DEFAULT_PASSWORD);

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        // Explicit registration: DriverManager only auto-discovers drivers via the
        // system classloader, so under `mvn exec:java` (isolated classloader) the
        // Connector/J service entry is invisible without this.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver (mysql-connector-j) is not on the classpath", e);
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Environment variable first, blank-safe fallback second. */
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}