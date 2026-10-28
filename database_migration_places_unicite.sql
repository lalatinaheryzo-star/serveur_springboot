-- =====================================================================
--  MIGRATION (NON EXECUTEE) : unicite des places et des paiements
--  A tester d'abord sur une base de TEST (copie), jamais directement en prod.
--  Additive : ne supprime ni ne modifie aucune donnee.
-- =====================================================================

-- ---- 0. PRE-CONTROLES : chaque requete doit retourner 0 ligne ------
-- 0.a Places liees a plusieurs reservations ACTIVES
SELECT place_id, COUNT(*) FROM reservations
 WHERE place_id IS NOT NULL AND statut IN ('En attente', 'Validée')
 GROUP BY place_id HAVING COUNT(*) > 1;

-- 0.b Numero de place en double pour un meme voyage
SELECT voyage_id, numero_place, COUNT(*) FROM places
 GROUP BY voyage_id, numero_place HAVING COUNT(*) > 1;

-- 0.c Plusieurs paiements pour une meme reservation
SELECT reservation_id, COUNT(*) FROM paiements
 GROUP BY reservation_id HAVING COUNT(*) > 1;

-- 0.d Reservations actives dont la place n'est pas 'reservee' (incoherence a corriger a la main)
SELECT r.id, r.place_id, p.statut FROM reservations r JOIN places p ON p.id = r.place_id
 WHERE r.statut IN ('En attente', 'Validée') AND p.statut <> 'reservee';

-- ---- 1. CONTRAINTES (a lancer seulement si 0.a, 0.b, 0.c = 0 ligne) ---
-- Une place ne peut avoir qu'UNE reservation active.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_place_active
    ON reservations (place_id)
    WHERE place_id IS NOT NULL AND statut IN ('En attente', 'Validée');

-- Un numero de place est unique dans un voyage.
CREATE UNIQUE INDEX IF NOT EXISTS uq_place_voyage_numero
    ON places (voyage_id, numero_place);

-- Un seul paiement par reservation.
CREATE UNIQUE INDEX IF NOT EXISTS uq_paiement_reservation
    ON paiements (reservation_id);

-- ---- 2. ROLLBACK ------------------------------------------------------
-- DROP INDEX IF EXISTS uq_reservation_place_active;
-- DROP INDEX IF EXISTS uq_place_voyage_numero;
-- DROP INDEX IF EXISTS uq_paiement_reservation;
