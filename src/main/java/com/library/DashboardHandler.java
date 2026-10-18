package com.library;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * {@code GET /api/dashboard} — summary widgets for the librarian dashboard:
 * total books, copies currently issued, overdue count, active members, and copies
 * available right now. Requires a session.
 */
public class DashboardHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    private record Summary(int totalBooks, int availableNow, int issuedNow, int overdue, int activeMembers) {
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        SessionManager.Session session = Auth.session(ex);
        if (session == null) {
            send(ex, 401, Map.of("error", "Not authenticated"));
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SUMMARY_SQL);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            Summary s = new Summary(
                    rs.getInt("total_books"),
                    rs.getInt("available_now"),
                    rs.getInt("issued_now"),
                    rs.getInt("overdue"),
                    rs.getInt("active_members"));
            send(ex, 200, s);
        } catch (SQLException e) {
            send(ex, 500, Map.of("error", e.getMessage() == null ? "Database error" : e.getMessage()));
        }
    }

    private static final String SUMMARY_SQL = """
            SELECT
                (SELECT COALESCE(SUM(total_copies), 0) FROM books) AS total_books,
                (SELECT COALESCE(SUM(available_copies), 0) FROM books) AS available_now,
                (SELECT COUNT(*) FROM issue_records WHERE status = 'ISSUED') AS issued_now,
                (SELECT COUNT(*) FROM issue_records WHERE status = 'ISSUED' AND due_date < CURDATE()) AS overdue,
                (SELECT COUNT(*) FROM members WHERE active_status = 1) AS active_members
            """;

    private void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
