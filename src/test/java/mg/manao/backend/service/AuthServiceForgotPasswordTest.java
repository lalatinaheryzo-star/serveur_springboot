package mg.manao.backend.service;

import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceForgotPasswordTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailExistenceValidator emailExistenceValidator;
    @Mock
    private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                utilisateurRepository,
                passwordEncoder,
                jwtService,
                emailExistenceValidator,
                emailService);
    }

    @Test
    void forgotPasswordDoesNothingForUnknownAddress() {
        when(utilisateurRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(" unknown@example.com ");

        verify(emailService, never()).envoyerNouveauMotDePasse(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPasswordChangesPasswordAndEmailsItWithoutReturningIt() {
        Utilisateur user = Utilisateur.builder()
                .nom("Voyageur")
                .prenom("Ada")
                .email("ada@example.com")
                .motDePasse("old-hash")
                .role(Utilisateur.Role.VOYAGEUR)
                .codeReinitialisation("old-reset-code-hash")
                .build();
        when(utilisateurRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
        when(emailService.envoyerNouveauMotDePasse(anyString(), anyString(), anyString())).thenReturn(true);

        authService.forgotPassword("ada@example.com");

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(emailService).envoyerNouveauMotDePasse(
                eq("ada@example.com"), eq("Ada"), password.capture());
        assertEquals(10, password.getValue().length());
        assertEquals("hash:" + password.getValue(), user.getMotDePasse());
        assertNull(user.getCodeReinitialisation());
        assertNull(user.getCodeReinitialisationExpiration());
        verify(utilisateurRepository).save(user);
    }
}
