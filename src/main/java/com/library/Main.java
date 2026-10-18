package com.library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    private static final Path INDEX = Path.of("web", "index.html");

    private static byte[] indexHtml;

    public static void main(String[] args) throws IOException {
        indexHtml = Files.readAllBytes(INDEX);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/books", new BookHandler());
        server.createContext("/", new IndexHandler());
        server.start();
        System.out.println("Server running on http://localhost:8080");
    }

    private static class IndexHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, indexHtml.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(indexHtml);
            }
        }
    }
}