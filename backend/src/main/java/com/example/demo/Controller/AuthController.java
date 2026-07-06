package com.example.demo.Controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * AuthController — تسجيل الدخول + إدارة المستخدمين.
 *
 * POST   /api/auth/login                       → تسجيل الدخول (مفتوح)
 * POST   /api/auth/register                    → إنشاء مستخدم (ADMIN فقط)
 * GET    /api/auth/users                       → قائمة المستخدمين (ADMIN فقط)
 * PUT    /api/auth/users/{id}                  → تعديل مستخدم (ADMIN فقط)        🆕
 * POST   /api/auth/users/{id}/password         → تغيير كلمة سر مستخدم (ADMIN)    🆕
 * DELETE /api/auth/users/{id}                  → حذف مستخدم (ADMIN فقط)
 * POST   /api/auth/me/password                 → تغيير كلمة سري (أي مسجَّل)       🆕
 *
 * ── Super Admin model ──
 * الحساب username == "admin" هو الـ super admin:
 *   - لا يُحذَف
 *   - دوره (ADMIN) لا يتغيّر
 *   - كلمة سره لا يُغيّرها أحد إلا هو نفسه (حتى admin آخر لا يستطيع)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SUPER_ADMIN = "admin";

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void createDefaultAdmin() {
        if (userRepository.findByUsername(SUPER_ADMIN).isEmpty()) {
            User admin = new User();
            admin.setUsername(SUPER_ADMIN);
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("Administrator");
            admin.setRole(User.Role.ADMIN);
            userRepository.save(admin);
            System.out.println("✅ Default admin created: admin/admin123");
        }
    }


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

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            result.add(toDto(u));
        }
        return ResponseEntity.ok(result);
    }

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

        return ResponseEntity.ok(toDto(newUser));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = opt.get();
        boolean isSuperAdmin = SUPER_ADMIN.equals(user.getUsername());

        if (body.containsKey("fullName")) {
            String fullName = body.get("fullName");
            if (fullName != null && !fullName.isBlank()) {
                user.setFullName(fullName.trim());
            }
        }

        if (body.containsKey("role")) {
            if (isSuperAdmin) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Cannot change the role of the Super Admin"));
            }
            String roleStr = body.get("role");
            if (roleStr != null) {
                try {
                    user.setRole(User.Role.valueOf(roleStr));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid role: " + roleStr));
                }
            }
        }

        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }


    @PostMapping("/users/{id}/password")
    public ResponseEntity<?> setUserPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = opt.get();

        if (SUPER_ADMIN.equals(user.getUsername())) {
            String caller = currentUsername();
            if (!SUPER_ADMIN.equals(caller)) {
                return ResponseEntity.status(403)
                        .body(Map.of("error",
                                "Super Admin's password can only be changed by themselves"));
            }
        }

        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 4) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 4 characters"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // SEC-1: Super Admin cannot be deleted
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent() && SUPER_ADMIN.equals(userOpt.get().getUsername())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot delete the Super Admin account"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(@RequestBody Map<String, String> body) {
        String username = currentUsername();
        if (username == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated"));
        }

        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "User not found"));
        }
        User user = opt.get();

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "oldPassword and newPassword are required"));
        }
        if (newPassword.length() < 4) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "New password must be at least 4 characters"));
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Current password is incorrect"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("ok", true));
    }

  private static Map<String, Object> toDto(User u) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("fullName", u.getFullName());
        map.put("role", u.getRole().name());
        map.put("superAdmin", SUPER_ADMIN.equals(u.getUsername()));
        return map;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getName();
    }
}
