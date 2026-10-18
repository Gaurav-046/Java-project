package com.library;

/**
 * A catalog title and its stock. One row per title: {@code totalCopies} owned,
 * {@code availableCopies} currently on the shelf.
 *
 * <p>The 5-argument constructor is a compatibility convenience used by existing code and
 * tests; it supplies sensible defaults (generic genre, Available condition, one copy).
 */
public record Book(
        int id,
        String title,
        String author,
        String isbn,
        int publishYear,
        String genre,
        String condition,
        int totalCopies,
        int availableCopies) {

    /** Compatibility constructor: defaults genre/condition/stock for a single copy. */
    public Book(int id, String title, String author, String isbn, int publishYear) {
        this(id, title, author, isbn, publishYear, null, "Available", 1, 1);
    }

    /** Conditions a title can be flagged with. */
    public static final String AVAILABLE = "Available";
    public static final String DAMAGED = "Damaged";
    public static final String LOST = "Lost";
}
