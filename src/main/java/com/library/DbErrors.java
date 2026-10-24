package com.library;

import java.sql.SQLException;

/**
 * Translates integrity-constraint failures (SQLSTATE class 23: primary key, unique and
 * foreign key violations) into messages a librarian can act on instead of raw driver text.
 * Works for PostgreSQL (SQLSTATE-based, e.g. 23505/23503) and MySQL (vendor error numbers
 * 1062/1451/1452), so the same handlers answer 409 for constraint clashes and 500 for
 * everything else via {@link #status} on either database.
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

    /** True for a duplicate-key / unique-constraint violation (MySQL 1062, SQLSTATE 23505). */
    static boolean duplicate(SQLException e) {
        return e.getErrorCode() == 1062 || "23505".equals(e.getSQLState());
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
        // MySQL identifies violations by vendor error number.
        switch (e.getErrorCode()) {
            case 1062:
                return duplicateMessage(entity);
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
                break;
        }
        // PostgreSQL (and other SQL-standard drivers) identify violations by SQLSTATE.
        String state = e.getSQLState();
        if (state != null) {
            switch (state) {
                case "23505":
                    return duplicateMessage(entity);
                case "23503":
                    // pgjdbc reports direction in the detail: a missing parent row on
                    // insert/update, or an existing child row blocking a delete.
                    String detail = e.getMessage();
                    if (detail != null && detail.contains("is not present")) {
                        return "Cannot save this " + entity
                                + ": a referenced record does not exist (foreign key constraint)";
                    }
                    if (detail != null && detail.contains("is still referenced")) {
                        return "Cannot delete or change this " + entity
                                + ": other records still reference it (foreign key constraint)";
                    }
                    return "This " + entity
                            + " conflicts with related records (foreign key constraint)";
                case "23502":
                    return "A required value is missing (the " + entity + " field cannot be null)";
                case "23514":
                    return "A value violates a check constraint while saving the " + entity;
                default:
                    break;
            }
        }
        String raw = e.getMessage();
        return "Database error while saving the " + entity
                + (raw == null || raw.isBlank() ? "" : ": " + raw);
    }

    private static String duplicateMessage(String entity) {
        return "This " + entity + " clashes with an existing record (duplicate key / unique value)";
    }
}
