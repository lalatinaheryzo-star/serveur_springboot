-- ============================================================
--  Migration : index de performance (aucune modification de structure)
--  À exécuter dans Supabase -> SQL Editor (une seule fois).
--  Purement additif : ajoute des index, ne touche à aucune colonne,
--  table, contrainte ou donnée existante.
-- ============================================================

-- Connexion / inscription / mot de passe oublié comparent l'e-mail sans
-- tenir compte de la casse (findByEmailIgnoreCase) : sans cet index sur
-- LOWER(email), chacun de ces appels — les plus fréquents de toute
-- l'application — scanne l'intégralité de la table utilisateurs.
-- UNIQUE ferme aussi une petite fenêtre de course possible côté
-- application : deux inscriptions simultanées avec la même adresse dans
-- une casse différente ne pourront plus jamais créer deux comptes.
CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateurs_email_lower
    ON utilisateurs (LOWER(email));

-- Réservations : utilisées pour "mes réservations" (voyageur), la liste du
-- Président (jointure sur voyages.cooperative_id), et le filtre par statut
-- lors de la validation.
CREATE INDEX IF NOT EXISTS idx_reservations_utilisateur_id ON reservations (utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_reservations_voyage_id      ON reservations (voyage_id);
CREATE INDEX IF NOT EXISTS idx_reservations_place_id       ON reservations (place_id);
CREATE INDEX IF NOT EXISTS idx_reservations_statut         ON reservations (statut);

-- Voyages : la liste "Mes voyages" du Président filtre par coopérative.
CREATE INDEX IF NOT EXISTS idx_voyages_cooperative_id ON voyages (cooperative_id);

-- Demandes de coopérative : liste Admin filtrée par statut, et jointure
-- utilisateur/coopérative lors de l'approbation.
CREATE INDEX IF NOT EXISTS idx_demandes_cooperatives_utilisateur_id ON demandes_cooperatives (utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_demandes_cooperatives_cooperative_id ON demandes_cooperatives (cooperative_id);
CREATE INDEX IF NOT EXISTS idx_demandes_cooperatives_statut         ON demandes_cooperatives (statut);
