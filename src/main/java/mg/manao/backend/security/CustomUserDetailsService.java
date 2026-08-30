package mg.manao.backend.security;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.repository.UtilisateurRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return utilisateurRepository.findByEmailIgnoreCase(email)
                .map(SecurityUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
    }

    public UserDetails loadUserById(UUID id) throws UsernameNotFoundException {
        return utilisateurRepository.findById(id)
                .map(SecurityUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + id));
    }
}
