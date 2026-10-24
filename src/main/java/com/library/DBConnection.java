package com.library;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    // Default target: local PostgreSQL 18, database `java`, password `root` (dev only).
    // Override any of these with DB_URL / DB_USER / DB_PASSWORD environment variables —
    // pointing DB_URL at jdbc:mysql:... keeps the old MySQL path working (the driver below
    // follows the URL scheme; MySQL additionally needs sql_mode=ANSI_QUOTES because the
    // SQL uses standard double-quoted identifiers such as "condition").
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/java";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "root";

    private static final String URL = env("DB_URL", DEFAULT_URL);
    private static final String USER = env("DB_USER", DEFAULT_USER);
    private static final String PASSWORD = env("DB_PASSWORD", DEFAULT_PASSWORD);

    private static final String DRIVER =
            URL.startsWith("jdbc:mysql:") ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        // Explicit registration: DriverManager only auto-discovers drivers via the
        // system classloader, so under `mvn exec:java` (isolated classloader) the
        // driver's service entry is invisible without this.
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException(DRIVER + " is not on the classpath", e);
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Environment variable first, blank-safe fallback second. */
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}