package mg.manao.backend.service;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.auth.AuthResponse;
import mg.manao.backend.dto.auth.LoginRequest;
import mg.manao.backend.dto.auth.RegisterRequest;
import mg.manao.backend.entity.Utilisateur;
import mg.manao.backend.exception.ApiException;
import mg.manao.backend.repository.UtilisateurRepository;
import mg.manao.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static mg.manao.backend.service.UtilisateurService.toDto;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(LoginRequest req) {
        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(req.getEmail().trim())
                .orElseThrow(() -> ApiException.unauthorized("Email ou mot de passe incorrect."));

        if (!passwordEncoder.matches(req.getPassword(), user.getMotDePasse())) {
            throw ApiException.unauthorized("Email ou mot de passe incorrect.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, toDto(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Cet email est déjà utilisé.");
        }
        Utilisateur user = Utilisateur.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .email(email)
                .telephone(req.getTelephone())
                .motDePasse(passwordEncoder.encode(req.getPassword()))
                .role(Utilisateur.Role.VOYAGEUR)
                .build();
        user = utilisateurRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, toDto(user));
    }
}
