-- ============================================================
--  Migration : vérification réelle de l'adresse e-mail (double opt-in)
--  À exécuter dans Supabase -> SQL Editor (une seule fois).
--  Additive uniquement : ne supprime ni ne renomme aucune colonne
--  ou table existante, donc sans impact sur les données actuelles.
-- ============================================================

ALTER TABLE utilisateurs
    ADD COLUMN IF NOT EXISTS email_verifie BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS code_verification TEXT,
    ADD COLUMN IF NOT EXISTS code_verification_expiration TIMESTAMPTZ;

-- Les comptes déjà existants en base (créés avant cette migration) sont
-- considérés vérifiés : ils se connectaient déjà normalement, on ne les
-- bloque pas rétroactivement. Seules les NOUVELLES inscriptions passent
-- désormais par /auth/register -> code de vérification -> /auth/verify-email.
UPDATE utilisateurs SET email_verifie = true WHERE email_verifie = false;

CREATE INDEX IF NOT EXISTS idx_utilisateurs_code_verification
    ON utilisateurs(code_verification)
    WHERE code_verification IS NOT NULL;
