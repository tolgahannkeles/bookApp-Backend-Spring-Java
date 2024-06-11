package com.example.bookApp.bookApp.controllers;

// ... (other imports)

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/books")
public class BookController {
    private final int recommendationBookCount = 10;
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(BookController.class);
    private final ObjectMapper objectMapper;  // Add ObjectMapper

    @Autowired
    public BookController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {  // Inject ObjectMapper
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("")
    public ResponseEntity<String> getAllBooksSimply() {
        logger.info("Fetching all books from the database");
        try {
            String sql = "SELECT id,name,imageLink FROM Book";

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql);

            // Convert to JSON using ObjectMapper
            String json = objectMapper.writeValueAsString(books);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json") // Set content type
                    .body(json);
        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting books to JSON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the request.");
        }
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<?> getAllBooksSimplyByCategory(@PathVariable Long categoryId) throws JsonProcessingException {
        logger.info("Fetching all books from the database according to categoryId: {}", categoryId);
        try {
            String sql = "SELECT b.id, b.name, b.imageLink FROM book b JOIN categoryJunction cj ON b.id = cj.bookId WHERE cj.categoryId = ?";

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql, categoryId);

            if (books.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "No books found for this category");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(objectMapper.writeValueAsString(errorResponse));
            } else {
                String json = objectMapper.writeValueAsString(books);
                return ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body(json);
            }
        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting books to JSON", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while processing the request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(objectMapper.writeValueAsString(errorResponse));
        }
    }


    @GetMapping("/{bookId}")
    public ResponseEntity<String> getBookById(@PathVariable Long bookId) {
        logger.info("Fetching book with ID: {}", bookId);
        try {
            String sql = "SELECT * FROM book WHERE id = ?"; // Use a placeholder for bookId

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql, bookId); // Pass bookId as parameter

            if (books.isEmpty()) {
                return ResponseEntity.notFound().build(); // 404 Not Found
            } else if (books.size() > 1) {
                logger.error("Multiple books found with the same ID: {}", bookId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Multiple books found with the same ID");
            } else {
                String json = objectMapper.writeValueAsString(books.get(0));
                return ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body(json);
            }
        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting book to JSON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the request.");
        }
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<?> getAllBooksSimplyByAuthorId(@PathVariable Long authorId) throws JsonProcessingException {
        logger.info("Fetching all books from the database according to authorId: {}", authorId);
        try {
            String sql = "SELECT b.id, b.name, b.imageLink FROM book b  WHERE authorId = ?";

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql, authorId);

            if (books.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "No books found for this category");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(objectMapper.writeValueAsString(errorResponse));
            } else {
                String json = objectMapper.writeValueAsString(books);
                return ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body(json);
            }
        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting books to JSON", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while processing the request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(objectMapper.writeValueAsString(errorResponse));
        }
    }

    @GetMapping("/recommendations")
    public ResponseEntity<String> getRandomBooks() {
        logger.info("Fetching {} random books", recommendationBookCount);
        try {
            // Get the total number of books in the database
            String countSql = "SELECT COUNT(*) FROM book";
            int totalBooks = jdbcTemplate.queryForObject(countSql, Integer.class);

            // Ensure the randomE value is valid
            int elementCount = Math.min(recommendationBookCount, totalBooks); // Don't select more books than exist

            // Construct the SQL query with random offset
            String sql = "SELECT * FROM book ORDER BY RAND() LIMIT ?";

            List<Map<String, Object>> randomBooks = jdbcTemplate.queryForList(sql, elementCount);

            String json = objectMapper.writeValueAsString(randomBooks);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(json);

        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting random books", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the request.");
        }
    }

    @GetMapping("/{bookId}/star")
    public ResponseEntity<String> getBookStars(@PathVariable Long bookId) {
        logger.info("Fetching star rating for book with ID: {}", bookId);
        try {
            String sql = "SELECT AVG(star) AS averageStar FROM BOOKSTARS WHERE bookId = ?";
            Double averageStar = jdbcTemplate.queryForObject(sql, Double.class, bookId);

            if (averageStar == null) {
                return ResponseEntity.ok("0.0"); // No ratings yet
            } else {
                return ResponseEntity.ok(String.format("%.1f", averageStar)); // Format to one decimal place
            }
        } catch (DataAccessException e) {
            logger.error("Error fetching star rating", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching star rating");
        }
    }




}
