package com.library;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory session store: token -> {@link Session}. Tokens are opaque UUIDs handed to the
 * client on login and presented back on the {@code Authorization: Bearer <token>} header.
 * Sessions expire after a fixed window and are pruned lazily on access.
 */
public final class SessionManager {

    /** One logged-in librarian, as a snapshot of the fields the UI needs. */
    public record Session(String token, int librarianId, String username, String fullName, String role) {
    }

    private record Entry(Session session, long expiresAt) {
    }

    private static final long TTL_MILLIS = TimeUnit.HOURS.toMillis(8);
    private static final Map<String, Entry> SESSIONS = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    /** Creates a fresh session for the librarian and returns its token-carrying record. */
    public static Session create(Librarian librarian) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, librarian.id(), librarian.username(),
                librarian.fullName(), librarian.role());
        SESSIONS.put(token, new Entry(session, System.currentTimeMillis() + TTL_MILLIS));
        return session;
    }

    /** Returns the live session for the token, or null when absent or expired. */
    public static Session get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String key = token.trim();
        prune();
        Entry entry = SESSIONS.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() < System.currentTimeMillis()) {
            SESSIONS.remove(key);
            return null;
        }
        return entry.session();
    }

    /** Invalidates a token on logout. Safe to call with null. */
    public static void invalidate(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
