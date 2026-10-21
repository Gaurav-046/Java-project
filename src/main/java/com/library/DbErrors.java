package com.library;

import java.sql.SQLException;

/**
 * Translates MySQL integrity-constraint failures (SQLSTATE class 23: primary key, unique and
 * foreign key violations) into messages a librarian can act on instead of raw driver text.
 * Wrapped exceptions keep their SQLState and vendor code, so handlers can answer 409 for
 * constraint clashes and 500 for everything else via {@link #status}.
 */
final class DbErrors {

    private DbErrors() {
    }

    /** True for primary-key / unique / foreign-key constraint violations. */
    static boolean integrity(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }

    /** HTTP status a handler should answer with: 409 for constraint clashes, 500 otherwise. */
    static int status(SQLException e) {
        return integrity(e) ? 409 : 500;
    }

    /**
     * Re-wraps a constraint failure with a friendly message while preserving SQLState and the
     * vendor error code, so {@link #status} and handler special-cases (e.g. duplicate ISBN)
     * keep working.
     */
    static SQLException wrap(SQLException e, String entity) {
        return new SQLException(message(e, entity), e.getSQLState(), e.getErrorCode(), e);
    }

    private static String message(SQLException e, String entity) {
        switch (e.getErrorCode()) {
            case 1062:
                return "This " + entity + " clashes with an existing record (duplicate key / unique value)";
            case 1451:
                return "Cannot delete or change this " + entity
                        + ": other records still reference it (foreign key constraint)";
            case 1452:
                return "Cannot save this " + entity
                        + ": a referenced record does not exist (foreign key constraint)";
            case 1048:
                return "A required value is missing (the " + entity + " field cannot be null)";
            case 1406:
                return "A value is too long for its column while saving the " + entity;
            case 3819:
                return "A value violates a check constraint while saving the " + entity;
            default:
                String detail = e.getMessage();
                return "Database error while saving the " + entity
                        + (detail == null || detail.isBlank() ? "" : ": " + detail);
        }
    }
}
