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
 * CRUD handler for {@code /api/members}. Requires a session; mutations are audited.
 * DELETE performs a soft delete (deactivate) so circulation history is preserved.
 */
public class MemberHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    private final MemberDAO dao = new MemberDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Cors.handle(ex)) {
            return;
        }
        SessionManager.Session session = Auth.session(ex);
        if (session == null) {
            send(ex, 401, error("Not authenticated"));
            return;
        }
        try {
            String method = ex.getRequestMethod();
            if (!method.equals("GET") && !method.equals("POST")
                    && !method.equals("PUT") && !method.equals("DELETE")) {
                send(ex, 405, error("Method not allowed"));
                return;
            }
            String rest = restOf(ex);
            if (rest.isEmpty()) {
                handleCollection(ex, method, session);
            } else {
                handleItem(ex, method, parseId(rest), session);
            }
        } catch (JsonSyntaxException | NumberFormatException e) {
            send(ex, 400, error("Bad request"));
        } catch (SQLException e) {
            send(ex, DbErrors.status(e), error(e.getMessage()));
        }
    }

    private void handleCollection(HttpExchange ex, String method, SessionManager.Session session)
            throws IOException, SQLException {
        switch (method) {
            case "GET" -> send(ex, 200, dao.findAll());
            case "POST" -> {
                Member member = Json.read(ex, Member.class);
                String problem = validationError(member);
                if (problem != null) {
                    send(ex, 400, error(problem));
                    return;
                }
                Member toCreate = new Member(0, member.name(), member.email(), member.phone(), true);
                dao.create(toCreate);
                ActivityLogger.log(session.librarianId(), ActivityLogger.ADD_MEMBER,
                        member.name());
                send(ex, 201, Map.of("message", "Member added"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
    }

    private void handleItem(HttpExchange ex, String method, int id, SessionManager.Session session)
            throws IOException, SQLException {
        switch (method) {
            case "GET" -> {
                Member member = dao.findById(id);
                if (member == null) {
                    send(ex, 404, error("Member not found"));
                } else {
                    send(ex, 200, member);
                }
            }
            case "PUT" -> {
                Member body = Json.read(ex, Member.class);
                Member updated = new Member(id, body.name(), body.email(), body.phone(), body.activeStatus());
                String problem = validationError(updated);
                if (problem != null) {
                    send(ex, 400, error(problem));
                    return;
                }
                if (dao.findById(id) == null) {
                    send(ex, 404, error("Member not found"));
                    return;
                }
                dao.update(updated);
                ActivityLogger.log(session.librarianId(), ActivityLogger.UPDATE_MEMBER,
                        "id " + id + " " + updated.name());
                send(ex, 200, Map.of("message", "Member updated"));
            }
            case "DELETE" -> {
                Member existing = dao.findById(id);
                if (existing == null) {
                    send(ex, 404, error("Member not found"));
                    return;
                }
                dao.deactivate(id);
                ActivityLogger.log(session.librarianId(), ActivityLogger.DEACTIVATE_MEMBER,
                        "id " + id + " " + existing.name());
                send(ex, 200, Map.of("message", "Member deactivated"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
    }

    /** Returns null when valid, otherwise a message for a 400 response. */
    static String validationError(Member m) {
        if (m == null) {
            return "Bad request";
        }
        if (m.name() == null || m.name().isBlank()) {
            return "Name is required";
        }
        if (m.name().length() > 100) {
            return "Name must be 100 characters or fewer";
        }
        if (m.email() != null && !m.email().isBlank()) {
            if (m.email().length() > 120) {
                return "Email must be 120 characters or fewer";
            }
            if (!m.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                return "Email must be a valid address";
            }
        }
        if (m.phone() != null && m.phone().length() > 30) {
            return "Phone must be 30 characters or fewer";
        }
        return null;
    }

    private String restOf(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        String rest = path.substring(ex.getHttpContext().getPath().length());
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }

    private int parseId(String rest) {
        if (!rest.startsWith("/")) {
            throw new NumberFormatException(rest);
        }
        return Integer.parseInt(rest.substring(1));
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
