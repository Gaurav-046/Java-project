-- Library System schema.
-- Idempotent: safe to run repeatedly against an existing database.

CREATE DATABASE IF NOT EXISTS library_db;

USE library_db;

-- ------------------------------------------------------------------ books --
-- One row per TITLE (stock model: total_copies owned, available_copies on shelf).
-- `condition`: Available | Damaged | Lost (a Damaged/Lost title also removes one
-- copy from available_copies so stock reflects reality). `condition` is a MySQL
-- reserved word, so it is always backtick-quoted.

CREATE TABLE IF NOT EXISTS books (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100) NOT NULL,
    isbn VARCHAR(20) UNIQUE,
    publish_year INT,
    genre VARCHAR(50),
    `condition` VARCHAR(20) NOT NULL DEFAULT 'Available',
    total_copies INT NOT NULL DEFAULT 1,
    available_copies INT NOT NULL DEFAULT 1
);

-- -------------------------------------------------------------- librarians --

CREATE TABLE IF NOT EXISTS librarians (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'LIBRARIAN'
);

-- ------------------------------------------------------------ members -------

CREATE TABLE IF NOT EXISTS members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(120),
    phone VARCHAR(30),
    active_status TINYINT(1) NOT NULL DEFAULT 1
);

-- ---------------------------------------------------------- issue_records ---
-- due_date = issue_date + loan window (14 days by policy). status: ISSUED | RETURNED.
-- fine_amount is recomputed on return for overdue loans.

CREATE TABLE IF NOT EXISTS issue_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    book_id INT NOT NULL,
    member_id INT NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    fine_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

-- ----------------------------------------------------------- activity_logs --

CREATE TABLE IF NOT EXISTS activity_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    librarian_id INT,
    action VARCHAR(50) NOT NULL,
    details VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (librarian_id) REFERENCES librarians(id) ON DELETE SET NULL
);

-- ------------------------------------------------------- seed librarian ----
-- Default development accounts. password_hash is PBKDF2-HMAC-SHA256 in the form
-- pbkdf2$<iterations>$<saltBase64>$<hashBase64>, produced by com.library.PasswordHasher.
--   admin     / admin123      (role ADMIN)
--   librarian / librarian123  (role LIBRARIAN)
-- CHANGE THESE PASSWORDS before any real deployment.

INSERT IGNORE INTO librarians (username, password_hash, full_name, role) VALUES
    ('admin',
     'pbkdf2$210000$2vMy+polkphFyO++NasP1w==$BqL5i0SnNGtfyXLlCapDUkjU2HiR6d27RUhGDy/M+QE=',
     'System Administrator', 'ADMIN'),
    ('librarian',
     'pbkdf2$210000$C5lHXQmF8DeedYdKD/U+eQ==$DPlZBfH+lueq0YldA/DQkKcTqR0tDvDWg1asctz7xLM=',
     'Library Staff', 'LIBRARIAN');

-- ============================================================ MIGRATION =====
-- For an EXISTING install whose `books` table predates the stock/genre/condition
-- columns, run the statements below once (MySQL 8 has no ADD COLUMN IF NOT EXISTS,
-- so they will error harmlessly with "Duplicate column name" if already applied):
--
-- ALTER TABLE books ADD COLUMN genre VARCHAR(50);
-- ALTER TABLE books ADD COLUMN `condition` VARCHAR(20) NOT NULL DEFAULT 'Available';
-- ALTER TABLE books ADD COLUMN total_copies INT NOT NULL DEFAULT 1;
-- ALTER TABLE books ADD COLUMN available_copies INT NOT NULL DEFAULT 1;
