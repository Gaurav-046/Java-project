package com.library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for {@code /api/books}. Every request requires a valid session (see {@link Auth}).
 * Supports CRUD, catalog search ({@code ?title=&author=&genre=&isbn=}), a stock condition
 * toggle, and CSV inventory import/export. Mutations are recorded to the audit log.
 */
public class BookHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    private final BookDAO dao = new BookDAO();

    private record ConditionRequest(String condition) {
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
            if (!method.equals("GET") && !method.equals("POST")
                    && !method.equals("PUT") && !method.equals("DELETE")) {
                send(ex, 405, error("Method not allowed"));
                return;
            }
            String rest = restOf(ex);
            if (rest.isEmpty()) {
                handleCollection(ex, method, session);
            } else {
                route(ex, method, rest, session);
            }
        } catch (JsonSyntaxException | NumberFormatException e) {
            send(ex, 400, error("Bad request"));
        } catch (MethodNotAllowed e) {
            send(ex, 405, error("Method not allowed"));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23000".equals(e.getSQLState())) {
                send(ex, 409, error("A book with this ISBN already exists"));
            } else {
                send(ex, 500, error(e.getMessage()));
            }
        }
    }

    // ------------------------------------------------------------- collection --

    private void handleCollection(HttpExchange ex, String method, SessionManager.Session session)
            throws IOException, SQLException {
        switch (method) {
            case "GET" -> send(ex, 200, listOrSearch(ex));
            case "POST" -> {
                Book body = readBook(ex);
                Book toCreate = forCreate(body);
                String problem = validationError(toCreate);
                if (problem != null) {
                    send(ex, 400, error(problem));
                    return;
                }
                dao.create(toCreate);
                ActivityLogger.log(session.librarianId(), ActivityLogger.ADD_BOOK, describe(toCreate));
                send(ex, 201, Map.of("message", "Book added"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
    }

    private List<Book> listOrSearch(HttpExchange ex) throws SQLException {
        Map<String, String> q = queryParams(ex);
        String title = q.get("title");
        String author = q.get("author");
        String genre = q.get("genre");
        String isbn = q.get("isbn");
        if (isAnyPresent(title, author, genre, isbn)) {
            return dao.search(title, author, genre, isbn);
        }
        return dao.findAll();
    }

    // ------------------------------------------------------------------ items --

    private void route(HttpExchange ex, String method, String rest, SessionManager.Session session)
            throws IOException, SQLException {
        if (!rest.startsWith("/")) {
            send(ex, 404, error("Not found"));
            return;
        }
        String[] seg = rest.substring(1).split("/");
        if (seg.length == 1 && "import".equals(seg[0])) {
            requireMethod(ex, method, "POST");
            handleImport(ex, session);
        } else if (seg.length == 1 && "export".equals(seg[0])) {
            requireMethod(ex, method, "GET");
            handleExport(ex);
        } else if (seg.length == 1 && isInt(seg[0])) {
            handleItem(ex, method, Integer.parseInt(seg[0]), session);
        } else if (seg.length == 2 && isInt(seg[0]) && "condition".equals(seg[1])) {
            requireMethod(ex, method, "PUT");
            handleCondition(ex, Integer.parseInt(seg[0]), session);
        } else {
            send(ex, 404, error("Not found"));
        }
    }

    private void handleItem(HttpExchange ex, String method, int id, SessionManager.Session session)
            throws IOException, SQLException {
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
                Book existing = dao.findById(id);
                if (existing == null) {
                    send(ex, 404, error("Book not found"));
                    return;
                }
                Book body = readBook(ex);
                Book updated = forUpdate(id, body, existing);
                String problem = validationError(updated);
                if (problem != null) {
                    send(ex, 400, error(problem));
                    return;
                }
                dao.update(updated);
                ActivityLogger.log(session.librarianId(), ActivityLogger.UPDATE_BOOK,
                        "id " + id + " -> " + describe(updated));
                send(ex, 200, Map.of("message", "Book updated"));
            }
            case "DELETE" -> {
                Book existing = dao.findById(id);
                dao.delete(id);
                ActivityLogger.log(session.librarianId(), ActivityLogger.DELETE_BOOK,
                        existing != null ? "id " + id + " " + describe(existing) : "id " + id);
                send(ex, 200, Map.of("message", "Book deleted"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
    }

    private void handleCondition(HttpExchange ex, int id, SessionManager.Session session)
            throws IOException, SQLException {
        ConditionRequest req = read(ex, ConditionRequest.class);
        String next = req.condition() == null ? null : req.condition().trim();
        if (!isValidCondition(next)) {
            send(ex, 400, error("Condition must be Available, Damaged, or Lost"));
            return;
        }
        Book existing = dao.findById(id);
        if (existing == null) {
            send(ex, 404, error("Book not found"));
            return;
        }
        int available = adjustAvailable(existing, next);
        dao.updateStock(id, next, available);
        ActivityLogger.log(session.librarianId(), ActivityLogger.UPDATE_CONDITION,
                "id " + id + " " + describe(existing) + ": " + existing.condition() + " -> " + next);
        send(ex, 200, Map.of("message", "Condition updated", "book", dao.findById(id)));
    }

    private void handleImport(HttpExchange ex, SessionManager.Session session) throws IOException, SQLException {
        String raw = readBody(ex);
        List<String[]> rows = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            if (!line.isBlank()) {
                rows.add(CsvUtil.splitLine(line));
            }
        }
        int added = 0;
        int skipped = 0;
        int errors = 0;
        boolean first = true;
        for (String[] cells : rows) {
            if (first) {
                first = false;
                if (CsvUtil.isHeader(cells)) {
                    continue;
                }
            }
            Book row = fromCsv(cells);
            if (validationError(row) != null) {
                errors++;
                continue;
            }
            if (row.isbn() != null && dao.isbnExists(row.isbn())) {
                skipped++;
                continue;
            }
            dao.create(row);
            added++;
        }
        ActivityLogger.log(session.librarianId(), ActivityLogger.IMPORT_BOOKS,
                "added=" + added + ", skipped=" + skipped + ", errors=" + errors);
        send(ex, 200, Map.of("added", added, "skipped", skipped, "errors", errors));
    }

    private void handleExport(HttpExchange ex) throws IOException, SQLException {
        String csv = CsvUtil.format(dao.findAll());
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"books.csv\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------- input shaping -----

    /** Normalises a create payload: fresh stock starts fully available. */
    private static Book forCreate(Book body) {
        String condition = blankToNull(body.condition()) == null ? Book.AVAILABLE : body.condition().trim();
        int total = Math.max(1, body.totalCopies());
        return new Book(0, body.title(), body.author(), body.isbn(), body.publishYear(),
                blankToNull(body.genre()), condition, total, total);
    }

    /**
     * Builds the updated row for a field edit. Condition is not changed here (it moves only
     * via the condition endpoint); available stock is re-clipped when total_copies changes.
     */
    private static Book forUpdate(int id, Book body, Book existing) {
        int newTotal = Math.max(1, body.totalCopies());
        int oldTotal = existing.totalCopies();
        int available;
        if (newTotal >= oldTotal) {
            available = existing.availableCopies() + (newTotal - oldTotal);
        } else {
            available = Math.min(existing.availableCopies(), newTotal);
        }
        available = Math.max(0, Math.min(available, newTotal));
        return new Book(id, body.title(), body.author(), body.isbn(), body.publishYear(),
                blankToNull(body.genre()), existing.condition(), newTotal, available);
    }

    /** Stock impact of a condition transition (Available <-> Damaged/Lost moves one copy). */
    static int adjustAvailable(Book b, String next) {
        boolean wasGood = Book.AVAILABLE.equals(b.condition());
        boolean nowGood = Book.AVAILABLE.equals(next);
        int available = b.availableCopies();
        if (wasGood && !nowGood) {
            available = Math.max(0, available - 1);
        } else if (!wasGood && nowGood) {
            available = Math.min(b.totalCopies(), available + 1);
        }
        return Math.max(0, Math.min(available, b.totalCopies()));
    }

    private static Book fromCsv(String[] cells) {
        String title = cells.length > 0 ? cells[0] : "";
        String author = cells.length > 1 ? cells[1] : "";
        String isbn = cells.length > 2 ? blankToNull(cells[2].trim()) : null;
        int year = cells.length > 3 ? parseIntOr(cells[3], 0) : 0;
        String genre = cells.length > 4 ? blankToNull(cells[4]) : null;
        String condition = cells.length > 5 && isValidCondition(cells[5].trim())
                ? cells[5].trim() : Book.AVAILABLE;
        int total = Math.max(1, cells.length > 6 ? parseIntOr(cells[6], 1) : 1);
        return new Book(0, title, author, isbn, year, genre, condition, total, total);
    }

    // ---------------------------------------------------------- validation -----

    /** Returns null when valid, otherwise a message suitable for a 400 response. */
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
        if (!isBlank(book.genre()) && book.genre().length() > 50) {
            return "Genre must be 50 characters or fewer";
        }
        if (!isBlank(book.condition()) && !isValidCondition(book.condition())) {
            return "Condition must be Available, Damaged, or Lost";
        }
        if (book.totalCopies() < 1) {
            return "Total copies must be at least 1";
        }
        if (book.availableCopies() < 0 || book.availableCopies() > book.totalCopies()) {
            return "Available copies must be between 0 and total copies";
        }
        return null;
    }

    private static boolean isValidCondition(String value) {
        if (isBlank(value)) {
            return true;
        }
        return Book.AVAILABLE.equals(value) || Book.DAMAGED.equals(value) || Book.LOST.equals(value);
    }

    // -------------------------------------------------------------- helpers ----

    private static String describe(Book b) {
        return "\"" + b.title() + "\" by " + b.author();
    }

    private String restOf(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        String rest = path.substring(ex.getHttpContext().getPath().length());
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }

    private static void requireMethod(HttpExchange ex, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new MethodNotAllowed();
        }
    }

    private static class MethodNotAllowed extends RuntimeException {
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean isAnyPresent(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String query = ex.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isIsbn(String value) {
        String digits = value.trim().replace("-", "");
        return digits.matches("\\d{10}") || digits.matches("\\d{13}");
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private Book readBook(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        if (body.isEmpty()) {
            throw new JsonSyntaxException("Empty request body");
        }
        Book book = GSON.fromJson(body, Book.class);
        if (book == null) {
            throw new JsonSyntaxException("Null request body");
        }
        return book;
    }

    private <T> T read(HttpExchange ex, Class<T> type) throws IOException {
        String body = readBody(ex);
        if (body.isEmpty()) {
            throw new JsonSyntaxException("Empty request body");
        }
        T value = GSON.fromJson(body, type);
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
