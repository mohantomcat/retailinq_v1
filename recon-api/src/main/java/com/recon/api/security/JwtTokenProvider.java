package com.recon.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId,
                                      String username, String tenantId,
                                      Set<String> permissions,
                                      Set<String> storeIds,
                                      boolean allStoreAccess,
                                      String authMode) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .claim("tenantId", tenantId)
                .claim("permissions",
                        new ArrayList<>(permissions))
                .claim("storeIds",
                        new ArrayList<>(storeIds))
                .claim("allStoreAccess", allStoreAccess)
                .claim("authMode", authMode)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis()
                                + jwtExpirationMs))
                .signWith(getSigningKey(),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return generateRefreshToken(userId, "PASSWORD");
    }

    public String generateRefreshToken(String userId,
                                       String authMode) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("type", "refresh")
                .claim("authMode", authMode)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis()
                                + refreshExpirationMs))
                .signWith(getSigningKey(),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return parseToken(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getPermissionsFromToken(String token) {
        List<String> perms = (List<String>) parseToken(token)
                .get("permissions");
        return perms != null
                ? new HashSet<>(perms) : new HashSet<>();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getStoreIdsFromToken(String token) {
        List<String> stores = (List<String>) parseToken(token)
                .get("storeIds");
        return stores != null
                ? new HashSet<>(stores) : new HashSet<>();
    }

    public String getTenantIdFromToken(String token) {
        return (String) parseToken(token).get("tenantId");
    }

    public String getUsernameFromToken(String token) {
        return (String) parseToken(token).get("username");
    }

    public boolean isAllStoreAccess(String token) {
        Boolean value = parseToken(token).get("allStoreAccess", Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    public String getAuthModeFromToken(String token) {
        return (String) parseToken(token).get("authMode");
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
