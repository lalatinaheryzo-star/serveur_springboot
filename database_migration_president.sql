-- ============================================================
--  Migration : rôle PRESIDENT + workflow "demande de coopérative"
--  À exécuter dans Supabase -> SQL Editor (une seule fois).
--  Additive uniquement : ne supprime ni ne renomme aucune colonne
--  ou table existante, donc sans impact sur les données actuelles.
-- ============================================================

-- 1) Nouveau rôle utilisateur -------------------------------------------------
-- Si vos utilisateurs.role est une colonne texte avec une contrainte CHECK,
-- adaptez le nom de la contrainte ci-dessous (\d utilisateurs pour le voir).
ALTER TABLE utilisateurs DROP CONSTRAINT IF EXISTS utilisateurs_role_check;
ALTER TABLE utilisateurs ADD CONSTRAINT utilisateurs_role_check
    CHECK (role IN ('ADMIN', 'PRESIDENT', 'VOYAGEUR'));

-- 2) Une coopérative <-> un seul Président (relation 1:1) --------------------
ALTER TABLE cooperatives
    ADD COLUMN IF NOT EXISTS president_id UUID UNIQUE REFERENCES utilisateurs(id);

-- 3) Table des demandes de création de coopérative ---------------------------
CREATE TABLE IF NOT EXISTS demandes_cooperatives (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id   UUID NOT NULL REFERENCES utilisateurs(id),
    nom_president    TEXT NOT NULL,
    telephone        TEXT NOT NULL,
    email            TEXT NOT NULL,
    cin              TEXT,
    nom_cooperative  TEXT NOT NULL,
    ville            TEXT,
    adresse          TEXT,
    message          TEXT,
    statut           TEXT NOT NULL DEFAULT 'PENDING'
                         CHECK (statut IN ('PENDING', 'APPROUVEE', 'REJETEE')),
    motif_rejet      TEXT,
    cooperative_id   UUID REFERENCES cooperatives(id),
    date_creation    TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_traitement  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_demandes_coop_utilisateur ON demandes_cooperatives(utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_demandes_coop_statut ON demandes_cooperatives(statut);

-- Un seul dossier "PENDING" à la fois par candidat (le service applicatif le
-- vérifie déjà, cet index le garantit aussi au niveau base de données).
CREATE UNIQUE INDEX IF NOT EXISTS uq_demande_pending_par_utilisateur
    ON demandes_cooperatives(utilisateur_id)
    WHERE statut = 'PENDING';
