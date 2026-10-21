package com.library;

import com.sun.net.httpserver.HttpExchange;

/** Extracts the session from an {@code Authorization: Bearer <token>} header. */
final class Auth {

    private Auth() {
    }

    /** Returns the live session for the request, or null when absent/invalid/expired. */
    static SessionManager.Session session(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return SessionManager.get(header.substring(7).trim());
    }
}
