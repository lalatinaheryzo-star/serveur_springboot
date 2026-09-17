-- ============================================================
--  Suppression de la vérification e-mail à l'inscription
--  (facultatif — aucune colonne n'est supprimée)
--
--  1) Les comptes restés bloqués "en attente de vérification"
--     deviennent actifs.
--  2) Les nouveaux comptes sont actifs par défaut.
--  3) Les codes de vérification d'inscription encore en base sont
--     effacés : les colonnes code_verification / code_verification_expiration
--     servent désormais au code "Mot de passe oublié" (hash BCrypt).
-- ============================================================
UPDATE utilisateurs SET email_verifie = TRUE WHERE email_verifie = FALSE;

ALTER TABLE utilisateurs ALTER COLUMN email_verifie SET DEFAULT TRUE;

UPDATE utilisateurs
   SET code_verification = NULL,
       code_verification_expiration = NULL
 WHERE code_verification IS NOT NULL;a
