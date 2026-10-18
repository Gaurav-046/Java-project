package com.library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

public class BookHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    private final BookDAO dao = new BookDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String rest = restOf(ex);

            if (!method.equals("GET") && !method.equals("POST")
                    && !method.equals("PUT") && !method.equals("DELETE")) {
                send(ex, 405, error("Method not allowed"));
                return;
            }

            if (rest.isEmpty()) {
                handleCollection(ex, method);
            } else {
                handleItem(ex, method, parseId(rest));
            }
        } catch (JsonSyntaxException | NumberFormatException e) {
            send(ex, 400, error("Bad request"));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23000".equals(e.getSQLState())) {
                send(ex, 409, error("A book with this ISBN already exists"));
            } else {
                send(ex, 500, error(e.getMessage()));
            }
        }
    }

    private void handleCollection(HttpExchange ex, String method) throws IOException, SQLException {
        switch (method) {
            case "GET" -> send(ex, 200, dao.findAll());
            case "POST" -> {
                Book book = readBook(ex);
                String problem = validationError(book);
                if (problem != null) {
                    send(ex, 400, error(problem));
                    return;
                }
                dao.create(book);
                send(ex, 201, Map.of("message", "Book added"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
    }

    private void handleItem(HttpExchange ex, String method, int id) throws IOException, SQLException {
        switch (method) {
            case "GET" -> {
                Book book = dao.findById(id);
                if (book == null) {
                    send(ex, 404, error("Book not found"));
                } else {
                    send(ex, 200, book);
                }
            }
            case "PUT" -> {
                Book body = readBook(ex);
                Book updated = new Book(id, body.title(), body.author(), body.isbn(), body.publishYear());
                String problem = validationError(updated);
                if (problem != null) {
                    send(ex, 400, error(problem));
                    return;
                }
                dao.update(updated);
                send(ex, 200, Map.of("message", "Book updated"));
            }
            case "DELETE" -> {
                dao.delete(id);
                send(ex, 200, Map.of("message", "Book deleted"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
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

    /**
     * Returns null when the book is valid, otherwise a message suitable for a 400 response.
     * Kept free of I/O and statics so it can be unit tested directly.
     */
    static String validationError(Book book) {
        if (book == null) {
            return "Bad request";
        }
        if (isBlank(book.title())) {
            return "Title is required";
        }
        if (book.title().length() > 200) {
            return "Title must be 200 characters or fewer";
        }
        if (isBlank(book.author())) {
            return "Author is required";
        }
        if (book.author().length() > 100) {
            return "Author must be 100 characters or fewer";
        }
        if (!isBlank(book.isbn()) && !isIsbn(book.isbn())) {
            return "ISBN must be 10 or 13 digits (hyphens allowed)";
        }
        int year = book.publishYear();
        if (year != 0 && (year < 1450 || year > LocalDate.now().getYear() + 1)) {
            return "Publish year must be between 1450 and " + (LocalDate.now().getYear() + 1);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isIsbn(String value) {
        String digits = value.trim().replace("-", "");
        return digits.matches("\\d{10}") || digits.matches("\\d{13}");
    }

    private Book readBook(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            throw new JsonSyntaxException("Empty request body");
        }
        Book book = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Book.class);
        if (book == null) {
            throw new JsonSyntaxException("Null request body");
        }
        return book;
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