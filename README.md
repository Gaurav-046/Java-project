# Library System

A small Java library management system: a JDBC/MySQL backend exposed through a JSON REST API, with a single-page HTML frontend.

Built with Maven, Java 17, `com.mysql:mysql-connector-j`, `com.google.code.gson:gson` and the JDK's built-in `com.sun.net.httpserver`.

## Prerequisites

- **JDK 17** or newer (`java -version`)
- **Apache Maven** 3.6+ (`mvn -v`)
- **MySQL 8** running on `localhost:3306`

## 1. Create the database

`schema.sql` (project root) creates the database and the `books` table:

```bash
mysql -u root -p < schema.sql
```

Or run it from the MySQL shell / your GUI client. It is idempotent (`CREATE DATABASE IF NOT EXISTS`).

## 2. Configure the connection (optional)

The app reads three environment variables and falls back to these defaults when they are unset:

| Variable     | Default                              |
|--------------|--------------------------------------|
| `DB_URL`     | `jdbc:mysql://localhost:3306/library_db` |
| `DB_USER`    | `root`                               |
| `DB_PASSWORD`| `your_password`                      |

Linux/macOS:

```bash
export DB_URL="jdbc:mysql://localhost:3306/library_db"
export DB_USER="root"
export DB_PASSWORD="your_password"
```

Windows (cmd):

```bat
set DB_URL=jdbc:mysql://localhost:3306/library_db
set DB_USER=root
set DB_PASSWORD=your_password
```

## 3. Run

Both commands must be run **from the project root**, because the page is served from `web/index.html`.

With the Maven exec plugin:

```bash
mvn compile exec:java -Dexec.mainClass=com.library.Main
```

As a single runnable jar (MySQL Connector/J and Gson are bundled):

```bash
mvn clean package
java -jar target/library-system-1.0.jar
```

Then open <http://localhost:8080>. Stop with `Ctrl+C`.

## 4. API

Base URL: `http://localhost:8080/api/books`. Every response is JSON with `Content-Type: application/json; charset=UTF-8`.

| Method | Path                | Success | Body on success            |
|--------|---------------------|---------|----------------------------|
| GET    | `/api/books`        | 200     | array of all books         |
| GET    | `/api/books/{id}`   | 200 / 404 | one book / `{"error":"Book not found"}` |
| POST   | `/api/books`        | 201     | `{"message":"Book added"}` |
| PUT    | `/api/books/{id}`   | 200     | `{"message":"Book updated"}` |
| DELETE | `/api/books/{id}`   | 200     | `{"message":"Book deleted"}` |

A book looks like:

```json
{"id": 1, "title": "The Hobbit", "author": "J.R.R. Tolkien", "isbn": "9780547928227", "publishYear": 1937}
```

Errors: `400` validation or bad request, `404` unknown id, `405` unsupported method, `409` duplicate ISBN, `500` server/DB failure - all as `{"error": "<message>"}`.

Examples with curl:

```bash
BASE=http://localhost:8080/api/books

# list all books
curl -s "$BASE"

# create a book
curl -s -i -X POST -H "Content-Type: application/json" \
  -d '{"title":"The Hobbit","author":"J.R.R. Tolkien","isbn":"9780547928227","publishYear":1937}' "$BASE"

# read one book (use the id returned above)
curl -s -i "$BASE/1"

# update a book
curl -s -i -X PUT -H "Content-Type: application/json" \
  -d '{"title":"The Hobbit (Revised)","author":"J.R.R. Tolkien","isbn":"9780547928227","publishYear":1937}' "$BASE/1"

# delete a book
curl -s -i -X DELETE "$BASE/1"

# an error case: unknown id -> 404
curl -s -i "$BASE/9999"
```

## 5. Screenshots

<!-- Replace the paths below with real screenshots once captured. -->
![Books list](docs/screenshot-books.png)

![Edit a book](docs/screenshot-edit.png)

## Tests

```bash
mvn test
```

Runs the JUnit 5 suite (`BookValidationTest`) for the request validation rules.