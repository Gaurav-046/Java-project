package com.library;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Circulation logic: issuing and returning books, enforcing the borrowing policy and
 * computing overdue fines. Stock ({@code available_copies}) is updated in the same
 * transaction as the issue record so book stock and loans can never drift apart.
 *
 * <p>Policy: a member may hold at most {@link #MAX_BOOKS_PER_MEMBER} books at once,
 * each loan runs {@link #LOAN_DAYS} days, and overdue books accrue
 * {@link #FINE_PER_DAY} per day late.
 */
public class IssueDAO {

    public static final int MAX_BOOKS_PER_MEMBER = 5;
    public static final int LOAN_DAYS = 14;
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("0.50");

    /** Domain error carrying the HTTP status the handler should reply with. */
    public static class CirculationException extends RuntimeException {
        private final int status;

        public CirculationException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** Issues a copy of a book to a member, enforcing stock and the borrowing limit. */
    public IssueRecord issue(int bookId, int memberId) {
        LocalDate today = LocalDate.now();
        LocalDate due = today.plusDays(LOAN_DAYS);
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Member member = memberForIssue(conn, memberId);
                Book book = bookForIssue(conn, bookId);
                requireUnderLimit(conn, memberId);

                // Decrement stock, guarded so it can never go below zero.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE books SET available_copies = available_copies - 1 "
                                + "WHERE id = ? AND available_copies > 0")) {
                    ps.setInt(1, bookId);
                    int stockUpdated;
                    try {
                        stockUpdated = ps.executeUpdate();
                    } catch (SQLException e) {
                        throw DbErrors.wrap(e, "book stock");
                    }
                    if (stockUpdated == 0) {
                        throw new CirculationException(409, "No copies available to issue");
                    }
                }

                int recordId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO issue_records (book_id, member_id, issue_date, due_date, status, fine_amount) "
                                + "VALUES (?, ?, ?, ?, 'ISSUED', 0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, bookId);
                    ps.setInt(2, memberId);
                    ps.setDate(3, Date.valueOf(today));
                    ps.setDate(4, Date.valueOf(due));
                    execute(ps, "issue record");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        recordId = keys.getInt(1);
                    }
                }
                conn.commit();
                return new IssueRecord(recordId, bookId, memberId, book.title(), member.name(),
                        today.toString(), due.toString(), null, "ISSUED", BigDecimal.ZERO);
            } catch (SQLException | CirculationException e) {
                rollback(conn);
                throw e;
            }
        } catch (SQLException e) {
            throw new CirculationException(500, e.getMessage());
        }
    }

    /** Returns an open loan, computing any overdue fine and restoring one copy to stock. */
    public IssueRecord returnBook(int recordId) {
        LocalDate today = LocalDate.now();
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                IssueRecord existing = findOpenRecord(conn, recordId);
                LocalDate due = LocalDate.parse(existing.dueDate());
                BigDecimal fine = computeFine(due, today);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE issue_records SET return_date = ?, status = 'RETURNED', fine_amount = ? WHERE id = ?")) {
                    ps.setDate(1, Date.valueOf(today));
                    ps.setBigDecimal(2, fine);
                    ps.setInt(3, recordId);
                    execute(ps, "issue record");
                }
                // Give one copy back, guarded so it never exceeds total_copies.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE books SET available_copies = available_copies + 1 "
                                + "WHERE id = ? AND available_copies < total_copies")) {
                    ps.setInt(1, existing.bookId());
                    execute(ps, "book stock");
                }
                conn.commit();
                return new IssueRecord(recordId, existing.bookId(), existing.memberId(),
                        existing.bookTitle(), existing.memberName(), existing.issueDate(),
                        existing.dueDate(), today.toString(), "RETURNED", fine);
            } catch (SQLException | CirculationException e) {
                rollback(conn);
                throw e;
            }
        } catch (SQLException e) {
            throw new CirculationException(500, e.getMessage());
        }
    }

    /** All loans, newest first, with book title and member name for display. */
    public List<IssueRecord> findAll() throws SQLException {
        String sql = """
                SELECT r.id, r.book_id, r.member_id, b.title AS book_title, m.name AS member_name,
                       r.issue_date, r.due_date, r.return_date, r.status, r.fine_amount
                FROM issue_records r
                LEFT JOIN books b ON b.id = r.book_id
                LEFT JOIN members m ON m.id = r.member_id
                ORDER BY r.id DESC""";
        List<IssueRecord> records = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                records.add(map(rs));
            }
        }
        return records;
    }

    // ---------------------------------------------------------------- helpers --

    /** Runs a write statement, translating PK/unique/FK constraint failures into readable errors. */
    private static void execute(PreparedStatement ps, String entity) throws SQLException {
        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DbErrors.wrap(e, entity);
        }
    }

    private Member memberForIssue(Connection conn, int memberId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, email, phone, active_status FROM members WHERE id = ?")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CirculationException(404, "Member not found");
                }
                boolean active = rs.getBoolean("active_status");
                if (!active) {
                    throw new CirculationException(409, "Member is not active");
                }
                return new Member(rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone"), active);
            }
        }
    }

    private Book bookForIssue(Connection conn, int bookId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, title, author, isbn, publish_year, genre, \"condition\", total_copies, available_copies "
                        + "FROM books WHERE id = ?")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CirculationException(404, "Book not found");
                }
                return BookDAO.map(rs);
            }
        }
    }

    private void requireUnderLimit(Connection conn, int memberId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM issue_records WHERE member_id = ? AND status = 'ISSUED'")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int open = rs.getInt(1);
                if (open >= MAX_BOOKS_PER_MEMBER) {
                    throw new CirculationException(409,
                            "Borrowing limit reached (" + MAX_BOOKS_PER_MEMBER + " books)");
                }
            }
        }
    }

    private IssueRecord findOpenRecord(Connection conn, int recordId) throws SQLException {
        String sql = """
                SELECT r.id, r.book_id, r.member_id, b.title AS book_title, m.name AS member_name,
                       r.issue_date, r.due_date, r.return_date, r.status, r.fine_amount
                FROM issue_records r
                LEFT JOIN books b ON b.id = r.book_id
                LEFT JOIN members m ON m.id = r.member_id
                WHERE r.id = ?""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CirculationException(404, "Issue record not found");
                }
                IssueRecord record = map(rs);
                if (!"ISSUED".equals(record.status())) {
                    throw new CirculationException(409, "This book has already been returned");
                }
                return record;
            }
        }
    }

    /** Overdue fine: whole days late multiplied by the per-day rate; 0 when on time. */
    static BigDecimal computeFine(LocalDate due, LocalDate returned) {
        long daysLate = ChronoUnit.DAYS.between(due, returned);
        if (daysLate <= 0) {
            return BigDecimal.ZERO;
        }
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(daysLate));
    }

    private static IssueRecord map(ResultSet rs) throws SQLException {
        Date returnDate = rs.getDate("return_date");
        return new IssueRecord(
                rs.getInt("id"),
                rs.getInt("book_id"),
                rs.getInt("member_id"),
                rs.getString("book_title"),
                rs.getString("member_name"),
                String.valueOf(rs.getDate("issue_date")),
                String.valueOf(rs.getDate("due_date")),
                returnDate == null ? null : returnDate.toString(),
                rs.getString("status"),
                rs.getBigDecimal("fine_amount"));
    }

    private static void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best-effort; the original error is what matters
        }
    }
}
