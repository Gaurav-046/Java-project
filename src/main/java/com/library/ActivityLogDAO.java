package com.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads audit history, newest first, joined with the librarian's display name. */
public class ActivityLogDAO {

    public List<ActivityLog> findRecent(int limit) throws SQLException {
        String sql = """
                SELECT l.id, l.librarian_id,
                       COALESCE(r.full_name, r.username, 'unknown') AS librarian_name,
                       l.action, l.details, l.timestamp
                FROM activity_logs l
                LEFT JOIN librarians r ON r.id = l.librarian_id
                ORDER BY l.id DESC
                LIMIT ?""";
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new ActivityLog(
                            rs.getInt("id"),
                            rs.getInt("librarian_id"),
                            rs.getString("librarian_name"),
                            rs.getString("action"),
                            rs.getString("details"),
                            String.valueOf(rs.getTimestamp("timestamp"))));
                }
            }
        }
        return logs;
    }
}
