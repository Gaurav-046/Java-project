package com.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data access for the {@code members} table. */
public class MemberDAO {

    public void create(Member m) throws SQLException {
        String sql = "INSERT INTO members (name, email, phone, active_status) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.name());
            ps.setString(2, emptyToNull(m.email()));
            ps.setString(3, emptyToNull(m.phone()));
            ps.setBoolean(4, m.activeStatus());
            ps.executeUpdate();
        }
    }

    public List<Member> findAll() throws SQLException {
        String sql = "SELECT id, name, email, phone, active_status FROM members ORDER BY active_status DESC, name";
        List<Member> members = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                members.add(map(rs));
            }
        }
        return members;
    }

    public Member findById(int id) throws SQLException {
        String sql = "SELECT id, name, email, phone, active_status FROM members WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    /** Updates the editable profile fields (including active_status). */
    public void update(Member m) throws SQLException {
        String sql = "UPDATE members SET name = ?, email = ?, phone = ?, active_status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.name());
            ps.setString(2, emptyToNull(m.email()));
            ps.setString(3, emptyToNull(m.phone()));
            ps.setBoolean(4, m.activeStatus());
            ps.setInt(5, m.id());
            ps.executeUpdate();
        }
    }

    /**
     * Soft delete: marks the member inactive instead of removing the row, so existing
     * circulation history (issue_records FK) stays intact.
     */
    public void deactivate(int id) throws SQLException {
        String sql = "UPDATE members SET active_status = 0 WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Members that count toward "active members" on the dashboard. */
    public int countActive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM members WHERE active_status = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Member map(ResultSet rs) throws SQLException {
        return new Member(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getBoolean("active_status"));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
