package com.library;

/** A library staff account. Never carries the password hash. */
public record Librarian(int id, String username, String fullName, String role) {
}
