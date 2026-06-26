package com.example.demo.Controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.Optional;

/**
 * AuthController — تسجيل الدخول + إدارة المستخدمين.
 *
 * POST /api/auth/login              → تسجيل الدخول (مفتوح)
 * POST /api/auth/register           → إنشاء مستخدم (ADMIN فقط)
 * GET  /api/auth/users              → قائمة المستخدمين (ADMIN فقط)
 * DELETE /api/auth/users/{id}       → حذف مستخدم (ADMIN فقط)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── Create default admin on first run ────────────────────────
    @PostConstruct
    public void createDefaultAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("Administrator");
            admin.setRole(User.Role.ADMIN);
            userRepository.save(admin);
            System.out.println("✅ Default admin created: admin/admin123");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIN — مفتوح للجميع
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid username or password"));
        }

        User user = userRepository.findByUsername(username).orElseThrow();
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("fullName", user.getFullName());
        response.put("role", user.getRole().name());

        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════════════════
    //  USER MANAGEMENT — ADMIN فقط
    // ══════════════════════════════════════════════════════════════

    /**
     * List all users (without passwords).
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("fullName", u.getFullName());
            map.put("role", u.getRole().name());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Create a new user.
     * Body: { "username": "viewer1", "password": "pass123", "fullName": "Viewer One", "role": "VIEWER" }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String fullName = body.get("fullName");
        String roleStr = body.getOrDefault("role", "VIEWER");

        if (username == null || password == null || fullName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username, password, and fullName are required"));
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username already exists"));
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setFullName(fullName);
        try {
            newUser.setRole(User.Role.valueOf(roleStr));
        } catch (IllegalArgumentException e) {
            newUser.setRole(User.Role.VIEWER);
        }
        userRepository.save(newUser);

        Map<String, Object> result = new HashMap<>();
        result.put("id", newUser.getId());
        result.put("username", newUser.getUsername());
        result.put("fullName", newUser.getFullName());
        result.put("role", newUser.getRole().name());

        return ResponseEntity.ok(result);
    }

    /**
     * Delete a user by ID.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // SEC-1: Super Admin cannot be deleted
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent() && "admin".equals(userOpt.get().getUsername())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot delete the Super Admin account"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
