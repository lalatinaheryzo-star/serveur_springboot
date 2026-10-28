-- ============================================================
--  Limitation des tentatives (connexion + code "mot de passe oublié")
--  À exécuter dans Supabase SQL Editor AVANT de déployer la version
--  du backend qui contient ces champs (spring.jpa.hibernate.ddl-auto=validate
--  refuse de démarrer si les colonnes manquent).
--
--  Additive uniquement : aucune colonne ni donnée n'est supprimée ou modifiée.
--  Les lignes existantes reçoivent les valeurs par défaut (0 / NULL).
--  NON APPLIQUÉ en production par l'assistant.
-- ============================================================
ALTER TABLE utilisateurs ADD COLUMN IF NOT EXISTS reset_tentatives       INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE utilisateurs ADD COLUMN IF NOT EXISTS reset_derniere_demande TIMESTAMPTZ NULL;
ALTER TABLE utilisateurs ADD COLUMN IF NOT EXISTS login_echecs           INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE utilisateurs ADD COLUMN IF NOT EXISTS login_verrou_jusqua    TIMESTAMPTZ NULL;
