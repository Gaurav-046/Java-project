package com.library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

/**
 * Circulation API for {@code /api/issues}.
 * <ul>
 *   <li>{@code GET  /api/issues} — list all loans (newest first)</li>
 *   <li>{@code POST /api/issues} — issue a copy ({@code {"bookId":..,"memberId":..}})</li>
 *   <li>{@code POST /api/issues/{id}/return} — return a loan and settle any fine</li>
 * </ul>
 * All routes require a session; issue/return are audited against the librarian.
 */
public class IssueHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    private final IssueDAO dao = new IssueDAO();

    private record IssueRequest(int bookId, int memberId) {
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        SessionManager.Session session = Auth.session(ex);
        if (session == null) {
            send(ex, 401, error("Not authenticated"));
            return;
        }
        try {
            String method = ex.getRequestMethod();
            if (!method.equals("GET") && !method.equals("POST")) {
                send(ex, 405, error("Method not allowed"));
                return;
            }
            String rest = restOf(ex);
            if (rest.isEmpty()) {
                if (method.equals("GET")) {
                    send(ex, 200, dao.findAll());
                } else {
                    issue(ex, session);
                }
            } else if (method.equals("POST") && rest.endsWith("/return")) {
                int id = parseId(rest.substring(0, rest.indexOf('/', 1)));
                returnBook(ex, id, session);
            } else {
                send(ex, 404, error("Not found"));
            }
        } catch (JsonSyntaxException | NumberFormatException e) {
            send(ex, 400, error("Bad request"));
        } catch (IssueDAO.CirculationException e) {
            send(ex, e.status(), error(e.getMessage()));
        } catch (SQLException e) {
            send(ex, 500, error(e.getMessage()));
        }
    }

    private void issue(HttpExchange ex, SessionManager.Session session) throws IOException {
        IssueRequest req = read(ex, IssueRequest.class);
        if (req.bookId() <= 0 || req.memberId() <= 0) {
            send(ex, 400, error("bookId and memberId are required"));
            return;
        }
        IssueRecord record = dao.issue(req.bookId(), req.memberId());
        ActivityLogger.log(session.librarianId(), ActivityLogger.ISSUE_BOOK,
                "Book id " + record.bookId() + " (" + record.bookTitle() + ") to "
                        + record.memberName() + ", due " + record.dueDate());
        send(ex, 201, Map.of("message", "Book issued", "record", record));
    }

    private void returnBook(HttpExchange ex, int id, SessionManager.Session session) throws IOException {
        IssueRecord record = dao.returnBook(id);
        String fine = record.fineAmount().signum() > 0
                ? ", fine $" + record.fineAmount().toPlainString() : "";
        ActivityLogger.log(session.librarianId(), ActivityLogger.RETURN_BOOK,
                "Record id " + id + " (" + record.bookTitle() + ")" + fine);
        send(ex, 200, Map.of("message", "Book returned", "record", record));
    }

    private String restOf(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        String rest = path.substring(ex.getHttpContext().getPath().length());
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }

    private int parseId(String segment) {
        if (segment == null || segment.isEmpty() || segment.charAt(0) != '/') {
            throw new NumberFormatException(String.valueOf(segment));
        }
        return Integer.parseInt(segment.substring(1));
    }

    private <T> T read(HttpExchange ex, Class<T> type) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            throw new JsonSyntaxException("Empty request body");
        }
        T value = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
        if (value == null) {
            throw new JsonSyntaxException("Null request body");
        }
        return value;
    }

    private void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message == null || message.isEmpty() ? "Unexpected error" : message);
    }
}
