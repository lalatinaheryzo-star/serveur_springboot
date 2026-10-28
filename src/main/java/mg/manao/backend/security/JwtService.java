package mg.manao.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mg.manao.backend.entity.Utilisateur;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // La clé doit faire au moins 256 bits (32 caractères) pour HS256.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "app.jwt.secret (JWT_SECRET) doit contenir au moins 32 caractères pour la signature HS256.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(Utilisateur user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * Vrai si le jeton a été émis AVANT le dernier changement de mot de passe de l'utilisateur.
     * La date `iat` du JWT a une précision d'une seconde : on compare donc à la seconde tronquée
     * (un jeton émis dans la même seconde que le changement reste accepté — fenêtre négligeable,
     * nécessaire pour que la connexion qui suit immédiatement une réinitialisation fonctionne).
     */
    public boolean isIssuedBeforePasswordChange(String token, Utilisateur user) {
        if (user.getMotDePasseModifieLe() == null) return false;
        Date iat = parseClaims(token).getIssuedAt();
        if (iat == null) return true; // jeton sans date d'émission : refusé par prudence
        long changeSec = user.getMotDePasseModifieLe().toEpochSecond();
        return iat.getTime() / 1000 < changeSec;
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
