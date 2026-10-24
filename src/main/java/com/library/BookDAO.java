package com.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data access for the {@code books} table, including stock, search and CSV helpers. */
public class BookDAO {

    private static final String COLUMNS =
            "id, title, author, isbn, publish_year, genre, \"condition\", total_copies, available_copies";

    public void create(Book b) throws SQLException {
        String sql = "INSERT INTO books (title, author, isbn, publish_year, genre, \"condition\", total_copies, available_copies) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, b, false);
            execute(ps, "book");
        }
    }

    public List<Book> findAll() throws SQLException {
        return query("SELECT " + COLUMNS + " FROM books ORDER BY id");
    }

    public Book findById(int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM books WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Updates the catalog fields and stock totals of a book. {@code availableCopies} is
     * written exactly as given, so callers compute/clip it before calling.
     */
    public void update(Book b) throws SQLException {
        String sql = "UPDATE books SET title = ?, author = ?, isbn = ?, publish_year = ?, "
                + "genre = ?, \"condition\" = ?, total_copies = ?, available_copies = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, b, true);
            execute(ps, "book");
        }
    }

    /** Sets only the condition and available stock (used by the condition toggle). */
    public void updateStock(int id, String condition, int availableCopies) throws SQLException {
        String sql = "UPDATE books SET \"condition\" = ?, available_copies = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, condition);
            ps.setInt(2, availableCopies);
            ps.setInt(3, id);
            execute(ps, "book");
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            execute(ps, "book");
        }
    }

    /** True when a book with this ISBN already exists (used to skip CSV duplicates). */
    public boolean isbnExists(String isbn) throws SQLException {
        if (isbn == null || isbn.isBlank()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM books WHERE isbn = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Case-insensitive catalog search. Each non-blank argument adds an AND-ed LIKE clause,
     * so any combination of title/author/genre/isbn can be filtered at once.
     */
    public List<Book> search(String title, String author, String genre, String isbn) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM books WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendLike(sql, params, "title", title);
        appendLike(sql, params, "author", author);
        appendLike(sql, params, "genre", genre);
        appendLike(sql, params, "isbn", isbn);
        sql.append(" ORDER BY id");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<Book> books = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(map(rs));
                }
            }
            return books;
        }
    }

    // ---------------------------------------------------------------- helpers --

    private static void appendLike(StringBuilder sql, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            params.add("%" + value.trim() + "%");
        }
    }

    private List<Book> query(String sql) throws SQLException {
        List<Book> books = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                books.add(map(rs));
            }
        }
        return books;
    }

    /** Runs a write statement, translating PK/unique/FK constraint failures into readable errors. */
    private static void execute(PreparedStatement ps, String entity) throws SQLException {
        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DbErrors.wrap(e, entity);
        }
    }

    private static void bind(PreparedStatement ps, Book b, boolean withId) throws SQLException {
        ps.setString(1, b.title());
        ps.setString(2, b.author());
        ps.setString(3, b.isbn());
        ps.setInt(4, b.publishYear());
        ps.setString(5, b.genre());
        ps.setString(6, b.condition() == null || b.condition().isBlank() ? Book.AVAILABLE : b.condition());
        ps.setInt(7, b.totalCopies());
        ps.setInt(8, b.availableCopies());
        if (withId) {
            ps.setInt(9, b.id());
        }
    }

    static Book map(ResultSet rs) throws SQLException {
        String condition = rs.getString("condition");
        return new Book(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("isbn"),
                rs.getInt("publish_year"),
                rs.getString("genre"),
                condition == null || condition.isBlank() ? Book.AVAILABLE : condition,
                rs.getInt("total_copies"),
                rs.getInt("available_copies"));
    }
}
