package com.learning.security.utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import com.learning.security.exceptions.CustomJwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.learning.security.services.UserDetailsImpl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.SignatureException;

/**
 * <h2>JwtUtils</h2>
 * <p>
 * <b>Purpose:</b><br>
 * This utility class provides methods for generating, validating, and parsing JWT tokens.<br>
 * </p>
 * <ul>
 *   <li>Handles all JWT operations such as token creation, signature verification, and extracting claims.</li>
 *   <li>Centralizes JWT logic for use by authentication filters and controllers.</li>
 *   <li>Access tokens are short-lived (5 minutes) for security</li>
 *   <li>Use with refresh tokens for long-lived sessions</li>
 *   <li>Supports both symmetric (HS256) and asymmetric (RS256) signing algorithms</li>
 *   <li>RS256 allows external systems to verify tokens using public key</li>
 * </ul>
 * <p><b>When is it used?</b></p>
 * <ul>
 *   <li>Called by authentication filters and controllers during login, request validation, and user authentication.</li>
 * </ul>
 * <p><b>What happens after?</b></p>
 * <ul>
 *   <li>Tokens are generated for authenticated users, validated for incoming requests, and claims are extracted for authorization.</li>
 * </ul>
 */
@Component
public class JwtUtils {

    // Symmetric key configuration (HS256)
    @Value("${yousuf.app.jwtSecret}")
    private String jwtSecret;

    // Asymmetric key configuration (RS256)
    @Value("${yousuf.app.jwtSigningAlgorithm:RS256}")
    private String jwtSigningAlgorithm;
    
    @Value("${yousuf.app.rsaPrivateKeyPath:classpath:keys/private_key_pkcs8.pem}")
    private String rsaPrivateKeyPath;
    
    @Value("${yousuf.app.rsaPublicKeyPath:classpath:keys/public_key.pem}")
    private String rsaPublicKeyPath;

    @Value("${yousuf.app.jwtExpirationTimeInMs}")
    private int jwtExpirationTimeInMs;
    
    private final ResourceLoader resourceLoader;

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    static final String BUILD_REF = "TS4gWW91c3VmIOKAlCBodHRwczovL2dpdGh1Yi5jb20veW91c3VmLWdpdCDigJQgaHR0cHM6Ly95b3VzdWYtZGV2LmNvbQ==";

    // Cache for loaded keys
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private SecretKey symmetricKey;
    
    public JwtUtils(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    /**
     * Get JWT expiration time in milliseconds
     */
    public long getJwtExpirationMs() {
        return jwtExpirationTimeInMs;
    }
    
    /**
     * Get public key as PEM string for external systems
     */
    public String getPublicKeyPem() throws IOException {
        if ("RS256".equals(jwtSigningAlgorithm)) {
            Resource resource = resourceLoader.getResource(rsaPublicKeyPath);
            return new String(resource.getInputStream().readAllBytes());
        }
        throw new UnsupportedOperationException("Public key is only available for RS256 algorithm");
    }
    
    /**
     * Load RSA private key from PEM file
     */
    private PrivateKey loadPrivateKey() throws Exception {
        if (privateKey != null) {
            return privateKey;
        }
        
        Resource resource = resourceLoader.getResource(rsaPrivateKeyPath);
        String key = new String(resource.getInputStream().readAllBytes())
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        privateKey = kf.generatePrivate(spec);
        return privateKey;
    }
    
    /**
     * Load RSA public key from PEM file
     */
    private PublicKey loadPublicKey() throws Exception {
        if (publicKey != null) {
            return publicKey;
        }
        
        Resource resource = resourceLoader.getResource(rsaPublicKeyPath);
        String key = new String(resource.getInputStream().readAllBytes())
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        publicKey = kf.generatePublic(spec);
        return publicKey;
    }
    
    /**
     * Get symmetric secret key (HS256)
     */
    private SecretKey getSymmetricKey() {
        if (symmetricKey != null) {
            return symmetricKey;
        }
        symmetricKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return symmetricKey;
    }
    
    /**
     * <h3>generateTokenByAuth</h3>
     * <p>
     * <b>Purpose:</b><br>
     * Generates a short-lived JWT access token (5 minutes) for an authenticated user.<br>
     * Supports both symmetric (HS256) and asymmetric (RS256) signing.<br>
     * </p>
     * <ul>
     *   <li>Uses user details from the authentication object to set claims and expiration.</li>
     *   <li>Signs the token with either symmetric secret key or RSA private key based on configuration.</li>
     *   <li>Short expiration enhances security - use with refresh tokens</li>
     *   <li>RS256 tokens can be verified by external systems using public key</li>
     * </ul>
     * <p><b>When is it called?</b></p>
     * <ul>
     *   <li>After successful authentication during login or token refresh.</li>
     * </ul>
     * <p><b>What happens after?</b></p>
     * <ul>
     *   <li>The generated token is sent to the client for use in subsequent requests.</li>
     *   <li>Client should refresh token before expiration using refresh token.</li>
     * </ul>
     * @param auth the Authentication object containing user details
     * @return the generated JWT token as a String
     */
    public String generateTokenByAuth(Authentication auth) {

        // Only valid authentication object should reach here
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();

        // Extract role (e.g., "ROLE_CUSTOMER")
        String role = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .findFirst()
                .orElse("");
        
        // Build scopes array from permissions (if any) - space-separated string
        // This retrieves permissions from the user's role
        String scopes = "";
        if (userDetails.getUser() != null && 
            userDetails.getUser().getRole() != null && 
            userDetails.getUser().getRole().getPermissions() != null) {
            scopes = userDetails.getUser().getRole().getPermissions().stream()
                    .map(p -> p.getName())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
        }

        try {
            // https://github.com/jwtk/jjwt?tab=readme-ov-file#creating-a-jwt
            var builder = Jwts.builder()
                    .header().add("typ", "JWT")
                    .and()
                    .issuer("M. Yousuf")
                    .subject(userDetails.getUsername())
                    .claim("role", role)
                    .claim("scopes", scopes)
                    .claim("user_id", userDetails.getId())
                    .issuedAt(new Date())
                    .expiration(new Date(new Date().getTime() + jwtExpirationTimeInMs));
            
            // Sign with appropriate algorithm
            if ("RS256".equals(jwtSigningAlgorithm)) {
                return builder.signWith(loadPrivateKey(), Jwts.SIG.RS256).compact();
            } else {
                // Default to HS256 for backward compatibility
                return builder.signWith(getSymmetricKey(), Jwts.SIG.HS256).compact();
            }
        } catch (Exception e) {
            logger.error("Error generating JWT token: {}", e.getMessage());
            throw new RuntimeException("Failed to generate JWT token", e);
        }
        
        // LEGACY CODE - Commented for reference (HS256 only implementation)
        // return Jwts.builder()
        //         .header().add("typ", "JWT")
        //         .and()
        //         .issuer("M. Yousuf")
        //         .subject(userDetails.getUsername())
        //         .issuedAt(new Date())
        //         .expiration(new Date(new Date().getTime() + jwtExpirationTimeInMs))
        //         .signWith(getSymmetricKey(), Jwts.SIG.HS256)
        //         .compact();
    }

    private SecretKey getKey() {
        // LEGACY METHOD - Kept for backward compatibility
        // Now wrapped by getSymmetricKey() method
        return getSymmetricKey();
        
        // SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        // logger.info("Key: {}", key);
        // logger.info("Length: {}", Decoders.BASE64.decode(jwtSecret).length);
        // return key;
    }

    /**
     * <h3>validateJwt</h3>
     * <p>
     * <b>Purpose:</b><br>
     * Validates the provided JWT token for structure, signature, and expiration.<br>
     * Supports both HS256 and RS256 verification based on configuration.<br>
     * </p>
     * <ul>
     *   <li>Throws a <code>CustomJwtException</code> if the token is invalid, expired, or malformed.</li>
     *   <li>Returns true if the token is valid.</li>
     *   <li>Uses appropriate key for verification based on signing algorithm</li>
     * </ul>
     * <p><b>When is it called?</b></p>
     * <ul>
     *   <li>During request filtering to ensure the token is valid before authentication.</li>
     * </ul>
     * <p><b>What happens after?</b></p>
     * <ul>
     *   <li>If valid, authentication proceeds; if not, error handling is triggered.</li>
     * </ul>
     * @param jwtToken the JWT token to validate
     * @return true if valid, otherwise throws exception
     */
    public boolean validateJwt(String jwtToken) {
        logger.debug("jwtToken: {}", jwtToken);
        try {
            // Verify with appropriate key based on algorithm
            if ("RS256".equals(jwtSigningAlgorithm)) {
                Jwts.parser()
                    .verifyWith(loadPublicKey())
                    .build().parseSignedClaims(jwtToken);
            } else {
                // Default to HS256 for backward compatibility
                Jwts.parser()
                    .verifyWith(getSymmetricKey())
                    .build().parseSignedClaims(jwtToken);
            }
            return true;
            
            // LEGACY CODE - Commented for reference (HS256 only)
            // Jwts.parser()
            //     .verifyWith(getSymmetricKey())
            //     .build().parseSignedClaims(jwtToken);
            // return true;
            
            // Handling the exceptions that can be thrown by parse()
            /*
             * 1. MalformedJwtException - if the specified JWT was incorrectly constructed 
             * 2. SignatureException - if a JWS signature was discovered, but could not be verified. 
             * 3. SecurityException - if the specified JWT string is a JWE and decryption fails
             * 4. ExpiredJwtException - if the specified JWT is a Claims JWT and the Claims has an expiration time before the time this method is invoked.
             * 5. IllegalArgumentException - if the specified string is null or empty or only whitespace.
             */
        } catch (MalformedJwtException e) {
            logger.error("JWT was incorrectly constructed: {}", e.getMessage());
            throw new CustomJwtException("JWT was incorrectly constructed: " + e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (SignatureException e) {
            logger.error("JWS signature was discovered, but could not be verified: {}", e.getMessage());
            throw new CustomJwtException("Invalid token signature", HttpServletResponse.SC_UNAUTHORIZED);
        } catch (SecurityException e) {
            logger.error("JWT string is a JWE and decryption fails: {}", e.getMessage());
            throw new CustomJwtException("JWT string is a JWE and decryption failed", HttpServletResponse.SC_BAD_REQUEST);
        } catch (ExpiredJwtException e) {
            logger.error("Token is expired: {}", e.getMessage());
            throw new CustomJwtException("Token expired: " + e.getMessage(),  HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IllegalArgumentException e) {
            logger.error("Token is null or empty or only whitespace: {}", e.getMessage());
            throw new CustomJwtException("Token is missing or invalid", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error validating JWT: {}", e.getMessage());
            throw new CustomJwtException("Token validation failed: " + e.getMessage(), HttpServletResponse.SC_UNAUTHORIZED);
        }
//        return false;

         
    }

    /**
     * <h3>getUsernameFromJwtToken</h3>
     * <p>
     * <b>Purpose:</b><br>
     * Extracts the username (subject) from the provided JWT token.<br>
     * Works with both HS256 and RS256 signed tokens.<br>
     * </p>
     * <ul>
     *   <li>Parses the token and retrieves the subject claim.</li>
     *   <li>Uses appropriate key for parsing based on signing algorithm</li>
     * </ul>
     * <p><b>When is it called?</b></p>
     * <ul>
     *   <li>After token validation, to identify the user making the request.</li>
     * </ul>
     * <p><b>What happens after?</b></p>
     * <ul>
     *   <li>The username is used to load user details and set authentication context.</li>
     * </ul>
     * @param jwtToken the JWT token
     * @return the username (subject) from the token
     */
    public String getUsernameFromJwtToken(String jwtToken) {
//      Steps   Build - Get claims - get subject (I've set username as subject)

        try {
            if ("RS256".equals(jwtSigningAlgorithm)) {
                return Jwts.parser()
                        .verifyWith(loadPublicKey())
                        .build()
                        .parseSignedClaims(jwtToken)
                        .getPayload().getSubject();
            } else {
                // Default to HS256 for backward compatibility
                return Jwts.parser()
                        .verifyWith(getSymmetricKey())
                        .build()
                        .parseSignedClaims(jwtToken)
                        .getPayload().getSubject();
            }
        } catch (Exception e) {
            logger.error("Error extracting username from JWT: {}", e.getMessage());
            throw new RuntimeException("Failed to extract username from token", e);
        }
        
        // LEGACY CODE - Commented for reference (HS256 only)
        // return Jwts.parser()
        //             .verifyWith(getSymmetricKey())
        //             .build()
        //             .parseSignedClaims(jwtToken)
        //             .getPayload().getSubject();
                    // .getHeader().get("Subject");
    }

    /**
     * Extract all claims from a JWT token for introspection.
     * Returns null if the token is invalid or expired.
     */
    public Claims getClaimsFromToken(String jwtToken) {
        try {
            if ("RS256".equals(jwtSigningAlgorithm)) {
                return Jwts.parser()
                        .verifyWith(loadPublicKey())
                        .build()
                        .parseSignedClaims(jwtToken)
                        .getPayload();
            } else {
                return Jwts.parser()
                        .verifyWith(getSymmetricKey())
                        .build()
                        .parseSignedClaims(jwtToken)
                        .getPayload();
            }
        } catch (Exception e) {
            logger.debug("Failed to extract claims from token: {}", e.getMessage());
            return null;
        }
    }
}
