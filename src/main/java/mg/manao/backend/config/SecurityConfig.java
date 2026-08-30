package mg.manao.backend.config;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ── Règles d'autorisation ──────────────────────────────────────
 * Publiques (aucun token requis) :
<<<<<<< HEAD
 *   - POST /auth/login, /auth/register, /auth/verify-email, /auth/resend-verification
=======
 *   - POST /auth/login, /auth/register, /auth/verify-email, /auth/resend-verification, /auth/forgot-password
>>>>>>> 6efaa14 (Backend Update)
 *   - GET  /voyages, /voyages/{id}, /voyages/{id}/places, /cooperatives
 *   - GET  /recus/verify/{token}, POST /recus/verify/{token}/checkin
 *       (page scannée par un agent de gare, sans compte — §17 de la spec)
 *
 * Authentifiées (contrôle fin fait dans les services/contrôleurs) :
 *   - GET  /auth/me
 *   - POST /places/{id}/reserver, /places/{id}/liberer
 *   - POST /paiements, /recus, /demandes-cooperatives
 *   - GET  /demandes-cooperatives/me, /cooperatives/me
 *   - GET  /recus/{id}, /recus/reservation/{id}, /recus/{id}/download
 *   - GET  /reservations, /paiements (filtré : VOYAGEUR -> les siennes,
 *       PRESIDENT -> celles de sa coopérative, ADMIN -> toutes)
 *
 * Réservées à PRESIDENT (§4 et §7 : ces actions quotidiennes appartiennent
 * désormais au Président de la coopérative concernée, plus à l'ADMIN) :
 *   - POST/PUT/DELETE /voyages
 *   - PUT  /cooperatives/{id} (uniquement la sienne, vérifié dans le service)
 *   - PATCH /reservations/{id}/statut (valider/refuser)
 *   - PATCH /paiements/{id}/statut (valider un paiement)
 *
 * Réservées à ADMIN (§1 : supervision globale uniquement) :
 *   - tout le CRUD /utilisateurs
 *   - DELETE /cooperatives (suppression = action de supervision ADMIN ; la création
 *       est désormais une action du Président lui-même après approbation, §2)
 *   - DELETE /voyages n'est PAS ici : voir ci-dessus (PRESIDENT)
 *   - DELETE /paiements, DELETE /recus, /notifications, /dashboard/stats
 *   - DELETE /reservations/{id}
 *   - GET /demandes-cooperatives (liste complète), PATCH .../approve, .../reject
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Public ──────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register",
<<<<<<< HEAD
                                                   "/auth/verify-email", "/auth/resend-verification").permitAll()
=======
                                                   "/auth/verify-email", "/auth/resend-verification",
                                                   "/auth/forgot-password").permitAll()
>>>>>>> 6efaa14 (Backend Update)
                .requestMatchers(HttpMethod.GET, "/voyages", "/voyages/**", "/cooperatives").permitAll()
                .requestMatchers(HttpMethod.GET, "/recus/verify/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/recus/verify/**").permitAll()

                // ── Président uniquement (§4/§7) ─────────────
                .requestMatchers(HttpMethod.POST,   "/voyages", "/cooperatives").hasRole("PRESIDENT")
                .requestMatchers(HttpMethod.PUT,    "/voyages/**", "/cooperatives/**").hasRole("PRESIDENT")
                .requestMatchers(HttpMethod.DELETE, "/voyages/**").hasRole("PRESIDENT")
                .requestMatchers(HttpMethod.PATCH,  "/reservations/*/statut").hasRole("PRESIDENT")
                .requestMatchers(HttpMethod.PATCH,  "/paiements/**").hasRole("PRESIDENT")

                // ── Admin uniquement (§1 : supervision) ──────
                .requestMatchers("/utilisateurs/**").hasRole("ADMIN")
                .requestMatchers("/notifications/**").hasRole("ADMIN")
                .requestMatchers("/dashboard/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/cooperatives/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/reservations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/paiements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/recus/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,    "/recus").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,    "/demandes-cooperatives").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/demandes-cooperatives/*/approve",
                                                     "/demandes-cooperatives/*/reject").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/demandes-cooperatives/**").hasRole("ADMIN")

                // ── Authentifié (ADMIN, PRESIDENT ou VOYAGEUR) — filtrage fin dans les services ──
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
