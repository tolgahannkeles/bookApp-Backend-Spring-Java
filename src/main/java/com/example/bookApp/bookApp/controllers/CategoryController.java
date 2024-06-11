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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(BookController.class);
    private final ObjectMapper objectMapper;  // Add ObjectMapper

    @Autowired
    public CategoryController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {  // Inject ObjectMapper
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("")
    public ResponseEntity<String> getCategories() {
        logger.info("Fetching all categories from the database");
        try {
            String sql = "SELECT * FROM CATEGORY";

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql);

            // Convert to JSON using ObjectMapper
            String json = objectMapper.writeValueAsString(books);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json") // Set content type
                    .body(json);
        } catch (DataAccessException | JsonProcessingException e) {
            logger.error("Error fetching or converting categories to JSON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the request.");
        }
    }
}
