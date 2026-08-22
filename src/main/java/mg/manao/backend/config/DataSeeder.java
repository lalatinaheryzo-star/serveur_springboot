package mg.manao.backend.config;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.entity.*;
import mg.manao.backend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Données de démonstration créées au premier démarrage (uniquement si les
 * tables sont vides et app.seed.enabled=true — voir application.properties).
 * Ne contient AUCUN secret réel : le mot de passe admin vient de
 * SEED_ADMIN_PASSWORD (à changer immédiatement en production).
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final CooperativeRepository cooperativeRepository;
    private final VoyageRepository voyageRepository;
    private final PlaceRepository placeRepository;
    private final DemandeCooperativeRepository demandeCooperativeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled}")
    private boolean seedEnabled;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) return;
        if (utilisateurRepository.count() > 0) return; // déjà initialisé

        utilisateurRepository.save(Utilisateur.builder()
                .nom("Admin").prenom("Plateforme")
                .email(adminEmail)
                .telephone("0340000000")
                .motDePasse(passwordEncoder.encode(adminPassword))
                .role(Utilisateur.Role.ADMIN)
                .build());

        utilisateurRepository.save(Utilisateur.builder()
                .nom("Rakoto").prenom("Voyageur")
                .email("voyageur@demo.mg")
                .telephone("0331234567")
                .motDePasse(passwordEncoder.encode("Voyageur123!"))
                .role(Utilisateur.Role.VOYAGEUR)
                .build());

        // Compte Président de démo, déjà approuvé, rattaché à la coopérative
        // ci-dessous — permet de tester tout de suite POST /voyages,
        // PATCH /reservations/{id}/statut, PATCH /paiements/{id}/statut, etc.
        Utilisateur president = utilisateurRepository.save(Utilisateur.builder()
                .nom("Randria").prenom("Président")
                .email("president@demo.mg")
                .telephone("0339998888")
                .motDePasse(passwordEncoder.encode("President123!"))
                .role(Utilisateur.Role.PRESIDENT)
                .build());

        Cooperative coop = cooperativeRepository.save(Cooperative.builder()
                .nom("Coopérative Fahazavana")
                .adresse("Gare routière Fasan'ny Karana, Antananarivo")
                .telephone("0341112233")
                .president(president)
                .build());

        Voyage voyage = voyageRepository.save(Voyage.builder()
                .villeDepart("Antananarivo")
                .villeArrivee("Antsirabe")
                .dateDepart(LocalDate.now().plusDays(3))
                .heureDepart(LocalTime.of(7, 30))
                .prix(new BigDecimal("25000"))
                .statut("actif")
                .cooperative(coop)
                .vehiculeNom("Sprinter")
                .capacite(18)
                .build());

        List<Place> places = new ArrayList<>();
        for (int i = 1; i <= voyage.getCapacite(); i++) {
            places.add(Place.builder().voyage(voyage).numeroPlace(i).statut("disponible").build());
        }
        placeRepository.saveAll(places);

        // Une demande de coopérative encore en attente, pour tester tout de
        // suite l'écran ADMIN d'approbation/rejet (§2 étape 3 de la spec).
        Utilisateur candidat = utilisateurRepository.save(Utilisateur.builder()
                .nom("Andria").prenom("Candidat")
                .email("candidat.president@demo.mg")
                .telephone("0338887777")
                .motDePasse(passwordEncoder.encode("Candidat123!"))
                .role(Utilisateur.Role.VOYAGEUR)
                .build());

        demandeCooperativeRepository.save(DemandeCooperative.builder()
                .utilisateur(candidat)
                .nomPresident("Andria Candidat")
                .telephone("0338887777")
                .email("candidat.president@demo.mg")
                .nomCooperative("Coopérative Vonjy")
                .ville("Antsirabe")
                .adresse("Gare routière Antsirabe")
                .statut(DemandeCooperative.Statut.PENDING)
                .build());

        System.out.println("=====================================================");
        System.out.println(" Données de démonstration créées :");
        System.out.println("  Admin       : " + adminEmail + " / " + adminPassword);
        System.out.println("  Voyageur    : voyageur@demo.mg / Voyageur123!");
        System.out.println("  Président   : president@demo.mg / President123!");
        System.out.println("  (candidat en attente d'approbation : candidat.president@demo.mg / Candidat123!)");
        System.out.println("  Coopérative : " + coop.getNom());
        System.out.println("  Voyage      : Antananarivo → Antsirabe");
        System.out.println(" ⚠️  Changez le mot de passe admin en production.");
        System.out.println("=====================================================");
    }
}
