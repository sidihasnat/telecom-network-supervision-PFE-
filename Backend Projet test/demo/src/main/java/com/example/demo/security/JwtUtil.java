package com.example.demo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JwtUtil — توليد وفحص JWT tokens.
 *
 * Token يحتوي:
 *   subject  = username
 *   role     = ADMIN / VIEWER
 *   iat      = وقت الإنشاء
 *   exp      = وقت الانتهاء (24 ساعة)
 */
@Component
public class JwtUtil {

    // ── Secret key (256-bit minimum for HS256) ──────────────
    // في الإنتاج: يُنقل لـ application.properties أو environment variable
    private static final String SECRET = "TelecomAI-JWT-Secret-Key-2026-Must-Be-At-Least-256-Bits-Long!!";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    // ── Token صالح لـ 24 ساعة ───────────────────────────────
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000;

    /**
     * توليد token جديد.
     */
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * استخراج username من token.
     */
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * استخراج role من token.
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * فحص هل الـ token صالح.
     */
    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
