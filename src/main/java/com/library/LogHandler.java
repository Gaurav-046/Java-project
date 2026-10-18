package com.library;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** {@code GET /api/logs} — recent audit history for the librarian UI. Requires a session. */
public class LogHandler implements HttpHandler {

    private static final Gson GSON = new Gson();
    private static final int MAX_ROWS = 200;

    private final ActivityLogDAO dao = new ActivityLogDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                send(ex, 405, Map.of("error", "Method not allowed"));
                return;
            }
            SessionManager.Session session = Auth.session(ex);
            if (session == null) {
                send(ex, 401, Map.of("error", "Not authenticated"));
                return;
            }
            List<ActivityLog> logs = dao.findRecent(MAX_ROWS);
            send(ex, 200, logs);
        } catch (SQLException e) {
            send(ex, 500, Map.of("error", e.getMessage() == null ? "Database error" : e.getMessage()));
        }
    }

    private void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
