-- =====================================================================
--  MIGRATION (NON EXECUTEE) : trace des rappels WhatsApp (anti-doublon)
--  Additive, rejouable. A appliquer AVANT de deployer le code (ddl-auto=validate).
-- =====================================================================
CREATE TABLE IF NOT EXISTS rappels_whatsapp (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id        UUID        NOT NULL UNIQUE REFERENCES reservations(id) ON DELETE CASCADE,
    statut                VARCHAR(20) NOT NULL,
    tentatives            INTEGER     NOT NULL DEFAULT 0,
    derniere_tentative_at TIMESTAMPTZ NOT NULL,
    envoye_at             TIMESTAMPTZ,
    derniere_erreur       VARCHAR(200)
);
CREATE INDEX IF NOT EXISTS idx_rappels_whatsapp_statut ON rappels_whatsapp (statut);

-- Rollback
-- DROP TABLE IF EXISTS rappels_whatsapp;
