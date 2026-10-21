package com.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Data access for the {@code librarians} table. */
public class LibrarianDAO {

    /**
     * A fetched credential row. Bundles the public librarian with its hash so callers can
     * verify a password, while keeping the hash out of the {@link Librarian} record.
     */
    public record Credentials(Librarian librarian, String passwordHash) {
    }

    /** Returns the matching credential row, or null when the username is unknown. */
    public Credentials findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, password_hash, full_name, role FROM librarians WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Credentials(
                            new Librarian(
                                    rs.getInt("id"),
                                    rs.getString("username"),
                                    rs.getString("full_name"),
                                    rs.getString("role")),
                            rs.getString("password_hash"));
                }
            }
        }
        return null;
    }
}
