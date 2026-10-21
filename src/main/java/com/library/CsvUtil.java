package com.library;

import java.util.ArrayList;
import java.util.List;

/** Minimal CSV parsing and formatting for inventory import/export. */
final class CsvUtil {

    private CsvUtil() {
    }

    /** Splits one CSV line, honouring double-quoted fields, escaped quotes and embedded commas. */
    static String[] splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    /** True when the first cell of the row looks like a header ("title", case-insensitive). */
    static boolean isHeader(String[] row) {
        return row.length > 0 && "title".equalsIgnoreCase(row[0].trim());
    }

    /** Renders books as CSV, quoting any field that contains a comma, quote or newline. */
    static String format(List<Book> books) {
        StringBuilder sb = new StringBuilder();
        sb.append("title,author,isbn,publish_year,genre,condition,total_copies,available_copies\n");
        for (Book b : books) {
            sb.append(quote(b.title())).append(',')
              .append(quote(b.author())).append(',')
              .append(quote(b.isbn())).append(',')
              .append(b.publishYear()).append(',')
              .append(quote(b.genre())).append(',')
              .append(quote(b.condition())).append(',')
              .append(b.totalCopies()).append(',')
              .append(b.availableCopies()).append('\n');
        }
        return sb.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
