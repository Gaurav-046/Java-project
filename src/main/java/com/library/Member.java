package com.library;

/** A borrowing member (student). {@code activeStatus=false} marks a deactivated member. */
public record Member(int id, String name, String email, String phone, boolean activeStatus) {
}
