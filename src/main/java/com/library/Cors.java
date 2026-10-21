package com.library;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * CORS support for the JSON API. Browsers answer any cross-origin request that carries an
 * Authorization header (or a JSON body) with a preflight {@code OPTIONS} request; if that
 * preflight is not satisfied the real POST/PUT/DELETE never happens. Every API handler
 * therefore calls {@link #handle} first.
 */
final class Cors {

    private Cors() {
    }

    /**
     * Tags the exchange with permissive CORS headers and answers an OPTIONS preflight with
     * 204. Returns true when the exchange was fully handled (a preflight), false when the
     * caller should continue with normal request processing.
     */
    static boolean handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        if (!"OPTIONS".equals(ex.getRequestMethod())) {
            return false;
        }
        ex.sendResponseHeaders(204, -1);
        ex.close();
        return true;
    }
}
