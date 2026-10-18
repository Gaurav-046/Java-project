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
 * Handles {@code POST /api/login} and {@code POST /api/logout}.
 *
 * <p>Login body: {@code {"username":"...","password":"..."}}. On success the response carries
 * an opaque session token plus the librarian's display details; the client presents it back on
 * every protected call as {@code Authorization: Bearer <token>}. Failed logins are audited and
 * answered with a uniform 401 that does not reveal whether the username exists.
 */
public class AuthHandler implements HttpHandler {

    private static final Gson GSON = new Gson();
    /**
     * Verified against when the username is unknown, so a login attempt costs the same time
     * whether or not the account exists (blunt username-enumeration defence).
     */
    private static final String DUMMY_HASH = PasswordHasher.hash("timing-equalizer");

    private final LibrarianDAO librarians = new LibrarianDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/login")) {
                login(ex);
            } else if (path.endsWith("/logout")) {
                logout(ex);
            } else {
                send(ex, 404, Map.of("error", "Not found"));
            }
        } catch (JsonSyntaxException e) {
            send(ex, 400, Map.of("error", "Bad request"));
        } catch (SQLException e) {
            send(ex, 500, Map.of("error", e.getMessage() == null ? "Database error" : e.getMessage()));
        }
    }

    private void login(HttpExchange ex) throws IOException, SQLException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Credentials body = readCredentials(ex);
        if (isBlank(body.username()) || isBlank(body.password())) {
            send(ex, 400, Map.of("error", "Username and password are required"));
            return;
        }
        LibrarianDAO.Credentials found = librarians.findByUsername(body.username().trim());
        String stored = found != null ? found.passwordHash() : DUMMY_HASH;
        boolean verified = PasswordHasher.verify(body.password(), stored);
        if (found == null || !verified) {
            if (found != null) {
                ActivityLogger.log(found.librarian().id(), ActivityLogger.FAILED_LOGIN, "Bad password");
            }
            send(ex, 401, Map.of("error", "Invalid username or password"));
            return;
        }
        SessionManager.Session session = SessionManager.create(found.librarian());
        ActivityLogger.log(found.librarian().id(), ActivityLogger.LOGIN, "Logged in");
        send(ex, 200, Map.of(
                "token", session.token(),
                "librarian", Map.of(
                        "id", session.librarianId(),
                        "username", session.username(),
                        "fullName", session.fullName(),
                        "role", session.role())));
    }

    private void logout(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        SessionManager.Session session = Auth.session(ex);
        if (session == null) {
            send(ex, 401, Map.of("error", "Not logged in"));
            return;
        }
        SessionManager.invalidate(session.token());
        ActivityLogger.log(session.librarianId(), ActivityLogger.LOGOUT, "Logged out");
        send(ex, 200, Map.of("message", "Logged out"));
    }

    private record Credentials(String username, String password) {
    }

    private Credentials readCredentials(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            throw new JsonSyntaxException("Empty request body");
        }
        Credentials body = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Credentials.class);
        if (body == null) {
            throw new JsonSyntaxException("Null request body");
        }
        return body;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
