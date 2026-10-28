package mg.manao.backend.service;

import mg.manao.backend.dto.auth.LoginRequest;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du parcours de réinitialisation par code (R1, R2, AUTH-03/06/07) et de la
 * limitation des connexions. L'encodeur est simulé de façon cohérente : encode(x) = "hash:" + x,
 * matches(x, h) = h.equals("hash:" + x).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailExistenceValidator emailExistenceValidator;
    @Mock private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(c -> "hash:" + c.getArgument(0));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenAnswer(c -> ("hash:" + c.getArgument(0)).equals(c.getArgument(1)));
        authService = new AuthService(utilisateurRepository, passwordEncoder, jwtService,
                emailExistenceValidator, emailService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private Utilisateur voyageur() {
        return Utilisateur.builder()
                .id(java.util.UUID.randomUUID())
                .nom("Voyageur").prenom("Ada").email("ada@example.com")
                .motDePasse("hash:ancien-mdp")
                .role(Utilisateur.Role.VOYAGEUR)
                .build();
    }

    private Utilisateur avecCodeActif(String code, int essais) {
        Utilisateur u = voyageur();
        u.setCodeReinitialisation("hash:" + code);
        u.setCodeReinitialisationExpiration(OffsetDateTime.now().plusMinutes(10));
        u.setResetTentatives(essais);
        return u;
    }

    private void trouve(Utilisateur u) {
        when(utilisateurRepository.findByEmailForUpdate("ada@example.com")).thenReturn(Optional.of(u));
    }

    // ───────────── R1 : ancien parcours désactivé ─────────────

    @Test
    void legacyForgotPasswordNeverChangesPasswordNorSendsOne() {
        Utilisateur u = voyageur();
        trouve(u);
        when(emailService.envoyerCodeReinitialisation(anyString(), anyString(), anyString())).thenReturn(true);

        authService.forgotPassword("ada@example.com");

        // Le mot de passe existant est intact, seul un code (hashé) est émis.
        assertEquals("hash:ancien-mdp", u.getMotDePasse());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).envoyerCodeReinitialisation(eq("ada@example.com"), eq("Ada"), code.capture());
        assertTrue(code.getValue().matches("\\d{6}"));
        assertEquals("hash:" + code.getValue(), u.getCodeReinitialisation());
    }

    // ───────────── Émission du code ─────────────

    @Test
    void resetCodeIsEmailedAndOnlyItsHashIsStored() {
        Utilisateur u = voyageur();
        trouve(u);
        when(emailService.envoyerCodeReinitialisation(anyString(), anyString(), anyString())).thenReturn(true);

        authService.genererNouveauCode(" ada@example.com ");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).envoyerCodeReinitialisation(eq("ada@example.com"), eq("Ada"), code.capture());
        assertTrue(code.getValue().matches("\\d{6}"));
        assertEquals("hash:" + code.getValue(), u.getCodeReinitialisation());
        assertTrue(u.getCodeReinitialisationExpiration().isAfter(OffsetDateTime.now()));
        assertEquals(0, u.getResetTentatives());
        assertNotNull(u.getResetDerniereDemande());
        // une sauvegarde par transaction courte : réservation de la demande, puis enregistrement du code
        verify(utilisateurRepository, org.mockito.Mockito.times(2)).save(u);
    }

    @Test
    void deliveryFailureKeepsThePreviousStillValidCode() {
        Utilisateur u = avecCodeActif("111111", 2);
        OffsetDateTime expirationAvant = u.getCodeReinitialisationExpiration();
        trouve(u);
        when(emailService.envoyerCodeReinitialisation(anyString(), anyString(), anyString())).thenReturn(false);

        authService.genererNouveauCode("ada@example.com");

        assertEquals("hash:111111", u.getCodeReinitialisation());
        assertEquals(expirationAvant, u.getCodeReinitialisationExpiration());
        assertEquals(2, u.getResetTentatives());
    }

    @Test
    void deliveryExceptionKeepsThePreviousCodeToo() {
        Utilisateur u = avecCodeActif("111111", 0);
        trouve(u);
        when(emailService.envoyerCodeReinitialisation(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("smtp down"));

        authService.genererNouveauCode("ada@example.com");

        assertEquals("hash:111111", u.getCodeReinitialisation());
    }

    @Test
    void newCodeReplacesThePreviousOneAndResetsTheAttemptCounter() {
        Utilisateur u = avecCodeActif("111111", 3);
        u.setResetDerniereDemande(OffsetDateTime.now().minusMinutes(5));
        trouve(u);
        when(emailService.envoyerCodeReinitialisation(anyString(), anyString(), anyString())).thenReturn(true);

        authService.genererNouveauCode("ada@example.com");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).envoyerCodeReinitialisation(anyString(), anyString(), code.capture());
        assertEquals("hash:" + code.getValue(), u.getCodeReinitialisation());
        assertEquals(0, u.getResetTentatives());
        // L'ancien code ne fonctionne plus.
        assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "111111", "nouveau-mdp"));
    }

    // ───────────── AUTH-03 : limitation des demandes ─────────────

    @Test
    void repeatedRequestWithinTheMinimumDelayIsSilentlyIgnored() {
        Utilisateur u = voyageur();
        u.setResetDerniereDemande(OffsetDateTime.now().minusSeconds(10));
        trouve(u);

        authService.genererNouveauCode("ada@example.com");

        verify(emailService, never()).envoyerCodeReinitialisation(anyString(), anyString(), anyString());
        assertNull(u.getCodeReinitialisation());
    }

    @Test
    void failedDeliveryStillConsumesTheDelay() {
        Utilisateur u = voyageur();
        trouve(u);
        when(emailService.envoyerCodeReinitialisation(anyString(), anyString(), anyString())).thenReturn(false);

        authService.genererNouveauCode("ada@example.com");
        authService.genererNouveauCode("ada@example.com"); // aussitôt après

        verify(emailService, org.mockito.Mockito.times(1))
                .envoyerCodeReinitialisation(anyString(), anyString(), anyString());
    }

    // ───────────── AUTH-07 : pas de divulgation ─────────────

    @Test
    void unknownAddressSendsNothingAndStoresNothing() {
        when(utilisateurRepository.findByEmailForUpdate("inconnu@example.com")).thenReturn(Optional.empty());

        authService.genererNouveauCode("inconnu@example.com");

        verify(emailService, never()).envoyerCodeReinitialisation(anyString(), anyString(), anyString());
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void adminAccountNeverReceivesAResetCode() {
        Utilisateur admin = voyageur();
        admin.setRole(Utilisateur.Role.ADMIN);
        trouve(admin);

        authService.genererNouveauCode("ada@example.com");

        verify(emailService, never()).envoyerCodeReinitialisation(anyString(), anyString(), anyString());
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void everyResetFailureCauseGivesTheSameMessage() {
        // compte inconnu
        when(utilisateurRepository.findByEmailForUpdate("inconnu@example.com")).thenReturn(Optional.empty());
        String inconnu = assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("inconnu@example.com", "123456", "nouveau-mdp")).getMessage();

        // compte sans code
        Utilisateur sansCode = voyageur();
        trouve(sansCode);
        String aucunCode = assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "nouveau-mdp")).getMessage();

        // code expiré
        Utilisateur expire = avecCodeActif("123456", 0);
        expire.setCodeReinitialisationExpiration(OffsetDateTime.now().minusMinutes(1));
        trouve(expire);
        String expireMsg = assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "nouveau-mdp")).getMessage();

        // mauvais code
        Utilisateur actif = avecCodeActif("123456", 0);
        trouve(actif);
        ApiException mauvais = assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "000000", "nouveau-mdp"));

        // compte ADMIN
        Utilisateur admin = avecCodeActif("123456", 0);
        admin.setRole(Utilisateur.Role.ADMIN);
        trouve(admin);
        String adminMsg = assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "nouveau-mdp")).getMessage();

        assertEquals(AuthService.MESSAGE_CODE_INVALIDE, inconnu);
        assertEquals(inconnu, aucunCode);
        assertEquals(inconnu, expireMsg);
        assertEquals(inconnu, mauvais.getMessage());
        assertEquals(inconnu, adminMsg);
        assertEquals(HttpStatus.BAD_REQUEST, mauvais.getStatus());
    }

    // ───────────── Réinitialisation : succès, usage unique, sécurité ─────────────

    @Test
    void validCodeResetsThePasswordWithAHashAndConsumesTheCode() {
        Utilisateur u = avecCodeActif("123456", 1);
        u.setLoginEchecs(3);
        trouve(u);

        authService.reinitialiserMotDePasse("ada@example.com", " 123456 ", "nouveau-mdp");

        assertEquals("hash:nouveau-mdp", u.getMotDePasse()); // hashé, jamais en clair
        assertNull(u.getCodeReinitialisation());
        assertNull(u.getCodeReinitialisationExpiration());
        assertEquals(0, u.getResetTentatives());
        assertEquals(0, u.getLoginEchecs());
        verify(utilisateurRepository).save(u);
    }

    @Test
    void aCodeCannotBeUsedTwice() {
        Utilisateur u = avecCodeActif("123456", 0);
        trouve(u);

        authService.reinitialiserMotDePasse("ada@example.com", "123456", "nouveau-mdp");
        assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "autre-mdp"));

        assertEquals("hash:nouveau-mdp", u.getMotDePasse());
    }

    @Test
    void expiredCodeIsRejectedAndErased() {
        Utilisateur u = avecCodeActif("123456", 0);
        u.setCodeReinitialisationExpiration(OffsetDateTime.now().minusMinutes(1));
        trouve(u);

        assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "nouveau-mdp"));

        assertEquals("hash:ancien-mdp", u.getMotDePasse());
        assertNull(u.getCodeReinitialisation());
    }

    @Test
    void wrongCodeNeverChangesThePasswordAndCountsAnAttempt() {
        Utilisateur u = avecCodeActif("123456", 0);
        trouve(u);

        assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "999999", "pirate-mdp"));

        assertEquals("hash:ancien-mdp", u.getMotDePasse());
        assertEquals(1, u.getResetTentatives());
        assertEquals("hash:123456", u.getCodeReinitialisation());
        verify(utilisateurRepository).save(u); // le compteur est bien persisté
    }

    // ───────────── AUTH-03/06 : plafond d'essais ─────────────

    @Test
    void codeIsInvalidatedAfterTooManyWrongAttemptsEvenIfTheNextOneIsRight() {
        Utilisateur u = avecCodeActif("123456", 0);
        trouve(u);

        for (int i = 0; i < AuthService.MAX_TENTATIVES_CODE; i++) {
            assertThrows(ApiException.class,
                    () -> authService.reinitialiserMotDePasse("ada@example.com", "000000", "pirate-mdp"));
        }

        assertNull(u.getCodeReinitialisation(), "le code doit être invalidé au plafond");
        // Même le BON code est maintenant refusé : il faut redemander un code.
        assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "pirate-mdp"));
        assertEquals("hash:ancien-mdp", u.getMotDePasse());
    }

    @Test
    void tooShortNewPasswordIsRejectedWithoutConsumingAnAttempt() {
        Utilisateur u = avecCodeActif("123456", 0);
        trouve(u);

        assertThrows(ApiException.class,
                () -> authService.reinitialiserMotDePasse("ada@example.com", "123456", "123"));

        assertEquals(0, u.getResetTentatives());
        assertEquals("hash:123456", u.getCodeReinitialisation());
    }

    // ───────────── AUTH-03 : connexion ─────────────

    private LoginRequest login(String email, String pwd) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(pwd);
        return r;
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        Utilisateur u = voyageur();
        u.setLoginEchecs(3);
        trouve(u);
        when(jwtService.generateToken(u)).thenReturn("jwt");

        assertEquals("jwt", authService.login(login("ada@example.com", "ancien-mdp")).getToken());

        assertEquals(0, u.getLoginEchecs());
    }

    @Test
    void wrongPasswordsLockTheAccountAndEvenTheRightPasswordIsRefusedWithTheSameMessage() {
        Utilisateur u = voyageur();
        trouve(u);

        String message = null;
        for (int i = 0; i < AuthService.MAX_ECHECS_CONNEXION; i++) {
            ApiException e = assertThrows(ApiException.class,
                    () -> authService.login(login("ada@example.com", "mauvais")));
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
            message = e.getMessage();
        }
        assertNotNull(u.getLoginVerrouJusqua());
        assertTrue(u.getLoginVerrouJusqua().isAfter(OffsetDateTime.now()));

        ApiException verrouille = assertThrows(ApiException.class,
                () -> authService.login(login("ada@example.com", "ancien-mdp")));
        assertEquals(HttpStatus.UNAUTHORIZED, verrouille.getStatus());
        assertEquals(message, verrouille.getMessage());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void unknownEmailGetsTheSameLoginFailureAsAWrongPassword() {
        when(utilisateurRepository.findByEmailForUpdate("inconnu@example.com")).thenReturn(Optional.empty());
        Utilisateur u = voyageur();
        trouve(u);

        ApiException inconnu = assertThrows(ApiException.class,
                () -> authService.login(login("inconnu@example.com", "x")));
        ApiException mauvais = assertThrows(ApiException.class,
                () -> authService.login(login("ada@example.com", "x")));

        assertEquals(inconnu.getMessage(), mauvais.getMessage());
        assertEquals(inconnu.getStatus(), mauvais.getStatus());
    }

    @Test
    void lockExpiryAllowsLoginAgain() {
        Utilisateur u = voyageur();
        u.setLoginVerrouJusqua(OffsetDateTime.now().minusMinutes(1));
        u.setLoginEchecs(0);
        trouve(u);
        when(jwtService.generateToken(u)).thenReturn("jwt");

        assertEquals("jwt", authService.login(login("ada@example.com", "ancien-mdp")).getToken());
        assertNull(u.getLoginVerrouJusqua());
    }

    @Test
    void lockDurationIsProgressiveAndCapped() {
        assertEquals(1, AuthService.dureeVerrouMinutes(5));
        assertEquals(4, AuthService.dureeVerrouMinutes(10));
        assertEquals(AuthService.VERROU_CONNEXION_MINUTES, AuthService.dureeVerrouMinutes(15));
        assertEquals(AuthService.VERROU_CONNEXION_MINUTES, AuthService.dureeVerrouMinutes(500));
    }

    @Test
    void firstSeriesOfFailuresLocksTheAccountOnlyAboutOneMinute() {
        Utilisateur u = voyageur();
        trouve(u);
        for (int i = 0; i < AuthService.MAX_ECHECS_CONNEXION; i++) {
            assertThrows(ApiException.class, () -> authService.login(login("ada@example.com", "mauvais")));
        }
        assertTrue(u.getLoginVerrouJusqua().isBefore(OffsetDateTime.now().plusMinutes(2)));
    }

    @Test
    void repeatedAbuseGetsALongerLock() {
        Utilisateur u = voyageur();
        u.setLoginEchecs(9);
        u.setLoginVerrouJusqua(OffsetDateTime.now().minusSeconds(1)); // verrou précédent expiré
        trouve(u);
        assertThrows(ApiException.class, () -> authService.login(login("ada@example.com", "mauvais")));
        assertTrue(u.getLoginVerrouJusqua().isAfter(OffsetDateTime.now().plusMinutes(3)));
    }
}
