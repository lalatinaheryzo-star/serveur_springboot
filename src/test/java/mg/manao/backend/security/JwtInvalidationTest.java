package mg.manao.backend.security;

import mg.manao.backend.entity.Utilisateur;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Groupe 4 : un JWT émis avant un changement de mot de passe est refusé. */
class JwtInvalidationTest {

    private final JwtService jwt = new JwtService("0123456789-0123456789-0123456789-xx", 3_600_000L);

    private Utilisateur user() {
        return Utilisateur.builder().id(UUID.randomUUID()).email("a@b.mg").role(Utilisateur.Role.VOYAGEUR).build();
    }

    @Test
    void tokenStaysValidWhenPasswordNeverChanged() {
        Utilisateur u = user();
        assertFalse(jwt.isIssuedBeforePasswordChange(jwt.generateToken(u), u));
    }

    @Test
    void tokenIssuedBeforePasswordChangeIsRejected() {
        Utilisateur u = user();
        String ancien = jwt.generateToken(u);
        u.setMotDePasseModifieLe(OffsetDateTime.now().plusSeconds(5));
        assertTrue(jwt.isIssuedBeforePasswordChange(ancien, u));
    }

    @Test
    void tokenIssuedAfterPasswordChangeIsAccepted() {
        Utilisateur u = user();
        u.setMotDePasseModifieLe(OffsetDateTime.now().minusMinutes(1));
        assertFalse(jwt.isIssuedBeforePasswordChange(jwt.generateToken(u), u));
    }

    @Test
    void loginRightAfterResetInTheSameSecondIsAccepted() {
        Utilisateur u = user();
        u.setMotDePasseModifieLe(OffsetDateTime.now());
        assertFalse(jwt.isIssuedBeforePasswordChange(jwt.generateToken(u), u));
    }
}
