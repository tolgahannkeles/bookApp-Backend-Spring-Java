package com.example.bookApp.bookApp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/authors")
public class AuthorController {
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(BookController.class);
    private final ObjectMapper objectMapper;  // Add ObjectMapper

    @Autowired
    public AuthorController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {  // Inject ObjectMapper
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    @GetMapping("")
    public ResponseEntity<String> getAuthors() {
        logger.info("Fetching all authors from the database");
        try {
            String sql = "SELECT * FROM AUTHOR";

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql);

            // Convert to JSON using ObjectMapper
            String json = objectMapper.writeValueAsString(books);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json") // Set content type
                    .body(json);
        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting authors to JSON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the request.");
        }
    }

    @GetMapping("/{authorId}")
    public ResponseEntity<String> getAuthorById(@PathVariable Long authorId) {
        logger.info("Fetching author with ID: {}", authorId);
        try {
            String sql = "SELECT * FROM AUTHOR WHERE id = ?"; // Use a placeholder for bookId

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql, authorId); // Pass bookId as parameter

            if (books.isEmpty()) {
                return ResponseEntity.notFound().build(); // 404 Not Found
            } else if (books.size() > 1) {
                logger.error("Multiple author found with the same ID: {}", authorId);
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
}
