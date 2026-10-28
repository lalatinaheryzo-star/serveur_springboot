-- =====================================================================
--  MIGRATION (NON EXECUTEE) : invalidation des JWT apres changement de mot de passe
--  Additive, rejouable. Colonne NULL = jamais change => tous les jetons restent valides.
--  A appliquer AVANT de deployer le code (ddl-auto=validate exige la colonne).
-- =====================================================================
ALTER TABLE utilisateurs
    ADD COLUMN IF NOT EXISTS mot_de_passe_modifie_le TIMESTAMPTZ;

-- Verification
-- SELECT column_name, data_type FROM information_schema.columns
--  WHERE table_name = 'utilisateurs' AND column_name = 'mot_de_passe_modifie_le';

-- Rollback
-- ALTER TABLE utilisateurs DROP COLUMN IF EXISTS mot_de_passe_modifie_le;
