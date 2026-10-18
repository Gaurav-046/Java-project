package com.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BookDAO {

    public void create(Book b) throws SQLException {
        String sql = "INSERT INTO books (title, author, isbn, publish_year) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.title());
            ps.setString(2, b.author());
            ps.setString(3, b.isbn());
            ps.setInt(4, b.publishYear());
            ps.executeUpdate();
        }
    }

    public List<Book> findAll() throws SQLException {
        String sql = "SELECT id, title, author, isbn, publish_year FROM books ORDER BY id";
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

    public Book findById(int id) throws SQLException {
        String sql = "SELECT id, title, author, isbn, publish_year FROM books WHERE id = ?";
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

    public void update(Book b) throws SQLException {
        String sql = "UPDATE books SET title = ?, author = ?, isbn = ?, publish_year = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.title());
            ps.setString(2, b.author());
            ps.setString(3, b.isbn());
            ps.setInt(4, b.publishYear());
            ps.setInt(5, b.id());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Book map(ResultSet rs) throws SQLException {
        return new Book(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("isbn"),
                rs.getInt("publish_year"));
    }
}