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
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/users")
public class UserController {
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(BookController.class);
    private final ObjectMapper objectMapper;  // Add ObjectMapper

    @Autowired
    public UserController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {  // Inject ObjectMapper
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<String> getUserById(@PathVariable Long userId) {
        logger.info("Fetching user with ID: {}", userId);
        try {
            String sql = "SELECT * FROM User WHERE id = ?"; // Use a placeholder for bookId

            List<Map<String, Object>> books = jdbcTemplate.queryForList(sql, userId); // Pass bookId as parameter

            if (books.isEmpty()) {
                return ResponseEntity.notFound().build(); // 404 Not Found
            } else if (books.size() > 1) {
                logger.error("Multiple author found with the same ID: {}", userId);
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

    @GetMapping("/{userId}/star/{bookId}")
    public ResponseEntity<?> getBookStars(@PathVariable Long userId, @PathVariable Long bookId) throws JsonProcessingException {
        logger.info("Fetching star rating for book with ID: {} and user ID: {}", bookId, userId);
        try {
            // Check if the book and user exist
            String bookExistsSql = "SELECT COUNT(*) FROM book WHERE id = ?";
            int bookCount = jdbcTemplate.queryForObject(bookExistsSql, Integer.class, bookId);
            if (bookCount == 0) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Book not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(objectMapper.writeValueAsString(errorResponse));
            }

            String userExistsSql = "SELECT COUNT(*) FROM user WHERE id = ?";
            int userCount = jdbcTemplate.queryForObject(userExistsSql, Integer.class, userId);
            if (userCount == 0) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "User not found");
                return ResponseEntity.badRequest().body(objectMapper.writeValueAsString(errorResponse));
            }

            String sql = "SELECT star FROM bookStars WHERE bookId = ? AND userId = ?";
            Integer star = jdbcTemplate.queryForObject(sql, Integer.class, bookId, userId);

            if (star == null) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Rating not found for this user and book");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(objectMapper.writeValueAsString(errorResponse));
            } else {
                return ResponseEntity.ok(String.valueOf(star));
            }
        } catch (DataAccessException e) {
            logger.error("Error fetching star rating", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while processing the request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(objectMapper.writeValueAsString(errorResponse));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    @PostMapping("/{userId}/star/{bookId}")
    public ResponseEntity<?> updateBookStars(@PathVariable Long userId, @PathVariable Long bookId, @RequestBody Map<String, Object> requestBody) throws JsonProcessingException {
        logger.info("Updating star rating for book with ID: {} and user ID: {}", bookId, userId);

        try {
            // Extract star from the request body (userId is already in the URL)
            int star = Integer.parseInt(requestBody.get("star").toString());

            // Validate star rating (1-5)
            if (star < 1 || star > 5) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid star rating. Please provide a value between 1 and 5.");
                return ResponseEntity.badRequest().body(objectMapper.writeValueAsString(errorResponse));
            }

            // Check if the book and user exist
            String bookExistsSql = "SELECT COUNT(*) FROM book WHERE id = ?";
            int bookCount = jdbcTemplate.queryForObject(bookExistsSql, Integer.class, bookId);
            if (bookCount == 0) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Book not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(objectMapper.writeValueAsString(errorResponse));
            }

            String userExistsSql = "SELECT COUNT(*) FROM user WHERE id = ?";
            int userCount = jdbcTemplate.queryForObject(userExistsSql, Integer.class, userId);
            if (userCount == 0) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "User not found");
                return ResponseEntity.badRequest().body(objectMapper.writeValueAsString(errorResponse));
            }

            // Update or insert the rating
            String upsertSql = "INSERT INTO bookStars (userId, bookId, star) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE star = VALUES(star)";
            jdbcTemplate.update(upsertSql, userId, bookId, star);

            // Return success message in JSON format
            Map<String, String> response = new HashMap<>();
            response.put("message", "Star rating updated successfully");
            return ResponseEntity.ok(objectMapper.writeValueAsString(response));

        } catch (DataAccessException e) {
            logger.error("Error updating star rating", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while processing the request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(objectMapper.writeValueAsString(errorResponse));
        } catch (NumberFormatException | JsonProcessingException e) {
            logger.error("Invalid input or JSON processing error", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid input or error processing JSON");
            return ResponseEntity.badRequest().body(objectMapper.writeValueAsString(errorResponse));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> requestBody) throws JsonProcessingException {
        logger.info("Login attempt with username: {}", requestBody.get("username"));

        try {
            String username = requestBody.get("username").toString();
            String password = requestBody.get("password").toString();

            // Query for the user by username
            String sql = "SELECT * FROM user WHERE username = ?";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, username);

            if (users.isEmpty()) {
                // User not found
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid username or password");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(objectMapper.writeValueAsString(errorResponse));
            }

            Map<String, Object> user = users.get(0);

            // In a real application, you'd hash the password and compare it here
            // For simplicity, we're just checking for equality
            if (password.equals(user.get("password"))) {
                // Remove the password field before returning the user object
                String json = objectMapper.writeValueAsString(user);
                return ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body(json);
            } else {
                // Incorrect password
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid username or password");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(objectMapper.writeValueAsString(errorResponse));
            }
        } catch (DataAccessException e) {
            logger.error("Error during login", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while processing the request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(objectMapper.writeValueAsString(errorResponse));
        }
    }


    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

    private boolean isValidUsername(String username) {
        return USERNAME_PATTERN.matcher(username).matches();
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isValidPassword(String password) {
        return PASSWORD_PATTERN.matcher(password).matches();
    }


    // Yardımcı Metotlar
    private boolean userExists(Long userId) {
        String sql = "SELECT COUNT(*) FROM user WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, userId) > 0;
    }



    private boolean isDuplicateUsername(String username, Long userId) {
        String sql = "SELECT COUNT(*) FROM user WHERE username = ? AND id != ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, username, userId) > 0;
    }

    private boolean isDuplicateUsername(String username) {
        String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, username) > 0;
    }



    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable Long userId, @RequestBody Map<String, Object> requestBody) {
        logger.info("Updating user with ID: {}", userId);

        try {
            // Kullanıcının varlığını kontrol et
            if (!userExists(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\": \"User not found\"}");
            }

            // JSON'dan değerleri al (null değerleri koruyarak)
            String username = (String) requestBody.get("username");
            String password = (String) requestBody.get("password");
            String name = (String) requestBody.get("name");
            String surname = (String) requestBody.get("surname");
            String imageLink = (String) requestBody.get("imageLink");

            // Kullanıcı adı ve şifre doğrulama (eğer sağlanmışlarsa)
            if (username != null && !isValidUsername(username)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Invalid username format\"}");
            }
            if (password != null && !isValidPassword(password)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Invalid password format\"}");
            }

            // Kullanıcı adı tekrarını kontrol et (eğer kullanıcı adı sağlanmışsa)
            if (username != null && isDuplicateUsername(username, userId)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Username already exists\"}");
            }

            // SQL güncelleme ifadesini oluştur ve parametreleri topla
            List<Object> params = new ArrayList<>();
            String updateSql = buildUpdateSql(username, password, name, surname, imageLink, userId, params);

            // Veritabanını güncelle
            jdbcTemplate.update(updateSql, params.toArray());

            // Güncellenmiş kullanıcı verilerini al ve döndür
            Map<String, Object> updatedUser = fetchUserById(userId);
            return ResponseEntity.ok(updatedUser); // Güncellenmiş kullanıcıyı döndür
        } catch (DataAccessException e) {
            logger.error("Error updating user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\": \"Failed to update user\"}");
        }
    }
    private Map<String, Object> fetchUserById(Long userId) {
        String sql = "SELECT * FROM user WHERE id = ?";
        return jdbcTemplate.queryForMap(sql, userId); // Kullanıcı verilerini Map olarak al
    }

    private String buildUpdateSql(String username, String password, String name, String surname, String imageLink, Long userId, List<Object> params) {
        StringBuilder sql = new StringBuilder("UPDATE user SET ");

        if (username != null) {
            sql.append("username = ?,"); // Virgül ekle
            params.add(username);
        }
        if (password != null) {
            sql.append("password = ?,"); // Virgül ekle
            params.add(password);
        }
        if (name != null) {
            sql.append("name = ?,"); // Virgül ekle
            params.add(name);
        }
        if (surname != null) {
            sql.append("surname = ?,"); // Virgül ekle
            params.add(surname);
        }
        if (imageLink != null) {
            sql.append("imageLink = ?,"); // Virgül ekle
            params.add(imageLink);
        }

        if (params.isEmpty()) {
            throw new IllegalArgumentException("No fields to update");
        }

        sql.setLength(sql.length() - 1); // Sondaki virgülü kaldır
        sql.append(" WHERE id = ?");
        params.add(userId);
        return sql.toString();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, Object> requestBody) {
        logger.info("Registering new user: {}", requestBody.get("username"));

        try {
            // JSON'dan değerleri al
            String name = (String) requestBody.get("name");
            String username = (String) requestBody.get("username");
            String password = (String) requestBody.get("password");

            // Zorunlu alanların kontrolü (null veya boş olmamalı)
            if (name == null || name.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"Name is required\"}");
            }
            if (username == null || username.isEmpty() || !isValidUsername(username)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Invalid username format\"}");
            }
            if (password == null || password.isEmpty() || !isValidPassword(password)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Invalid password format\"}");
            }

            // Kullanıcı adı tekrarını kontrol et
            if (isDuplicateUsername(username)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Username already exists\"}");
            }

            // SQL ekleme ifadesini oluştur ve veritabanına ekle
            String insertSql = "INSERT INTO user (name, username, password) VALUES (?, ?, ?)";
            jdbcTemplate.update(insertSql, name, username, password);

            // Yeni kullanıcının ID'sini al
            Long newUserId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            // Yeni kullanıcının verilerini al ve döndür
            Map<String, Object> newUser = fetchUserById(newUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
        } catch (DataAccessException e) {
            logger.error("Error registering user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\": \"Failed to register user\"}");
        }
    }



}
