package com.library;

import java.math.BigDecimal;

/**
 * One circulation loan. Dates are ISO strings ({@code yyyy-MM-dd}) so they serialize
 * cleanly to JSON. {@code bookTitle}/{@code memberName} are populated by the read query
 * for display and are not persisted columns.
 */
public record IssueRecord(
        int id,
        int bookId,
        int memberId,
        String bookTitle,
        String memberName,
        String issueDate,
        String dueDate,
        String returnDate,
        String status,
        BigDecimal fineAmount) {
}
