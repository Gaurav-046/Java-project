package com.library;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookValidationTest {

    private static final int CURRENT_YEAR_PLUS_ONE = java.time.LocalDate.now().getYear() + 1;

    @Test
    void validBookHasNoError() {
        assertNull(BookHandler.validationError(new Book(1, "The Hobbit", "J.R.R. Tolkien", "9780547928227", 1937)));
    }

    @Test
    void missingIsbnAndYearAreAllowed() {
        assertNull(BookHandler.validationError(new Book(1, "The Hobbit", "J.R.R. Tolkien", null, 0)));
        assertNull(BookHandler.validationError(new Book(1, "The Hobbit", "J.R.R. Tolkien", "", 0)));
    }

    @Test
    void hyphenatedIsbnIsAllowed() {
        assertNull(BookHandler.validationError(new Book(1, "Title", "Author", "978-0-13-468599-1", 2017)));
        assertNull(BookHandler.validationError(new Book(1, "Title", "Author", "0-13-468599-9", 2017)));
    }

    @Test
    void blankTitleIsRejected() {
        assertEquals("Title is required", BookHandler.validationError(new Book(1, "   ", "Author", null, 0)));
        assertEquals("Title is required", BookHandler.validationError(new Book(1, null, "Author", null, 0)));
    }

    @Test
    void blankAuthorIsRejected() {
        assertEquals("Author is required", BookHandler.validationError(new Book(1, "Title", "  ", null, 0)));
        assertEquals("Author is required", BookHandler.validationError(new Book(1, "Title", null, null, 0)));
    }

    @Test
    void overlongTitleIsRejected() {
        String title201 = "x".repeat(201);
        String title200 = "x".repeat(200);
        assertEquals("Title must be 200 characters or fewer",
                BookHandler.validationError(new Book(1, title201, "Author", null, 0)));
        assertNull(BookHandler.validationError(new Book(1, title200, "Author", null, 0)));
    }

    @Test
    void overlongAuthorIsRejected() {
        String author101 = "x".repeat(101);
        String author100 = "x".repeat(100);
        assertEquals("Author must be 100 characters or fewer",
                BookHandler.validationError(new Book(1, "Title", author101, null, 0)));
        assertNull(BookHandler.validationError(new Book(1, "Title", author100, null, 0)));
    }

    @Test
    void malformedIsbnIsRejected() {
        String error = BookHandler.validationError(new Book(1, "Title", "Author", "12345", 0));
        assertNotNull(error);
        assertTrue(error.contains("ISBN"), "expected an ISBN message but got: " + error);
    }

    @Test
    void nonDigitIsbnIsRejected() {
        String error = BookHandler.validationError(new Book(1, "Title", "Author", "978-0-ABC-468599-1", 0));
        assertNotNull(error);
        assertTrue(error.contains("ISBN"), "expected an ISBN message but got: " + error);
    }

    @Test
    void yearOutOfRangeIsRejected() {
        String tooOld = BookHandler.validationError(new Book(1, "Title", "Author", null, 1449));
        assertNotNull(tooOld);
        assertTrue(tooOld.contains("Publish year"), "expected a year message but got: " + tooOld);

        String tooNew = BookHandler.validationError(new Book(1, "Title", "Author", null, CURRENT_YEAR_PLUS_ONE + 1));
        assertNotNull(tooNew);
        assertTrue(tooNew.contains("Publish year"), "expected a year message but got: " + tooNew);
    }

    @Test
    void yearBoundsAreAccepted() {
        assertNull(BookHandler.validationError(new Book(1, "Title", "Author", null, 1450)));
        assertNull(BookHandler.validationError(new Book(1, "Title", "Author", null, CURRENT_YEAR_PLUS_ONE)));
    }
}