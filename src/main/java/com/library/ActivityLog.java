package com.library;

/** One audit-log row, joined with the librarian who performed it. */
public record ActivityLog(int id, int librarianId, String librarianName, String action,
                          String details, String timestamp) {
}
