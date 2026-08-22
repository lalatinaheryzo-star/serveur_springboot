package mg.manao.backend.service;

import mg.manao.backend.entity.Cooperative;
import mg.manao.backend.entity.Voyage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Rappel de voyage par WhatsApp — étape 1 : calcul uniquement.
 * Aucune tâche planifiée ici — voir RappelSchedulerService pour le déclenchement réel.
 */
@Service
public class RappelService {

    private static final ZoneId FUSEAU_MADAGASCAR = ZoneId.of("Indian/Antananarivo");

    public ZonedDateTime calculerHeureRappel(Voyage voyage, Cooperative cooperative) {
        if (cooperative == null || !cooperative.isRappelActif()) return null;
        if (voyage == null || voyage.getDateDepart() == null || voyage.getHeureDepart() == null) return null;

        LocalDateTime depart = LocalDateTime.of(voyage.getDateDepart(), voyage.getHeureDepart());
        LocalDateTime rappel = depart.minusMinutes(cooperative.getRappelDelaiMinutes());
        return rappel.atZone(FUSEAU_MADAGASCAR);
    }

    public boolean estMomentDenvoyerLeRappel(Voyage voyage, Cooperative cooperative) {
        ZonedDateTime heureRappel = calculerHeureRappel(voyage, cooperative);
        if (heureRappel == null) return false;
        ZonedDateTime maintenant = ZonedDateTime.now(FUSEAU_MADAGASCAR);
        return !maintenant.isBefore(heureRappel);
    }
}