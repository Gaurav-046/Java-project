package com.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Writes audit rows to {@code activity_logs}. Logging is best-effort: a failure to record
 * an action must never break the operation being audited, so errors are reported on stderr
 * and swallowed.
 */
public final class ActivityLogger {

    public static final String ADD_BOOK = "ADD_BOOK";
    public static final String UPDATE_BOOK = "UPDATE_BOOK";
    public static final String DELETE_BOOK = "DELETE_BOOK";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String FAILED_LOGIN = "FAILED_LOGIN";

    // circulation
    public static final String ISSUE_BOOK = "ISSUE_BOOK";
    public static final String RETURN_BOOK = "RETURN_BOOK";

    // members
    public static final String ADD_MEMBER = "ADD_MEMBER";
    public static final String UPDATE_MEMBER = "UPDATE_MEMBER";
    public static final String DEACTIVATE_MEMBER = "DEACTIVATE_MEMBER";

    // inventory
    public static final String UPDATE_CONDITION = "UPDATE_CONDITION";
    public static final String IMPORT_BOOKS = "IMPORT_BOOKS";

    private ActivityLogger() {
    }

    /** Records an action against a librarian. The timestamp column fills itself via DEFAULT. */
    public static void log(int librarianId, String action, String details) {
        String sql = "INSERT INTO activity_logs (librarian_id, action, details) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, librarianId);
            ps.setString(2, action);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[audit] failed to record " + action
                    + " for librarian " + librarianId + ": " + e.getMessage());
        }
    }
}
