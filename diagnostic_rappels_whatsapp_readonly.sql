-- Diagnostic en lecture seule pour l'erreur Hibernate sur rappels_whatsapp.
-- Executer ce fichier dans le SQL Editor Supabase de la base cible.
-- Chaque requete renvoie uniquement des metadonnees ; aucune donnee n'est modifiee.

-- 1. Identite de la connexion SQL et resolution des schemas.
SELECT
    current_database() AS database_name,
    current_schema() AS current_schema,
    current_setting('search_path') AS search_path;

-- 2. Tables visibles par le role SQL courant, dans tous les schemas accessibles.
SELECT table_schema, table_name, table_type
FROM information_schema.tables
WHERE table_name IN ('rappels_whatsapp', 'reservations')
  AND table_type = 'BASE TABLE'
ORDER BY table_schema, table_name;

-- 3. Noms et types exacts des colonnes de rappels_whatsapp.
-- Si la table est absente ou inaccessible, cette requete renvoie zero ligne.
SELECT
    c.table_schema,
    c.ordinal_position,
    c.column_name,
    c.data_type,
    c.udt_name,
    c.character_maximum_length,
    c.is_nullable,
    c.column_default
FROM information_schema.columns c
WHERE c.table_name = 'rappels_whatsapp'
  AND EXISTS (
      SELECT 1
      FROM information_schema.tables t
      WHERE t.table_schema = c.table_schema
        AND t.table_name = c.table_name
        AND t.table_type = 'BASE TABLE'
  )
ORDER BY c.table_schema, c.ordinal_position;

-- 4. Presence de reservations.id et son type exact.
-- Si la table est absente ou inaccessible, cette requete renvoie zero ligne.
SELECT
    c.table_schema,
    c.column_name,
    c.data_type,
    c.udt_name,
    c.is_nullable
FROM information_schema.columns c
WHERE c.table_name = 'reservations'
  AND c.column_name = 'id'
  AND EXISTS (
      SELECT 1
      FROM information_schema.tables t
      WHERE t.table_schema = c.table_schema
        AND t.table_name = c.table_name
        AND t.table_type = 'BASE TABLE'
  )
ORDER BY c.table_schema;

-- 5. Contraintes PK, UNIQUE et FK visibles sur rappels_whatsapp.
-- La definition detaille les colonnes et, pour une FK, sa cible et son action.
SELECT
    n.nspname AS table_schema,
    con.conname AS constraint_name,
    CASE con.contype
        WHEN 'p' THEN 'PRIMARY KEY'
        WHEN 'u' THEN 'UNIQUE'
        WHEN 'f' THEN 'FOREIGN KEY'
    END AS constraint_type,
    (
        SELECT string_agg(a.attname, ', ' ORDER BY k.ordinality)
        FROM unnest(con.conkey) WITH ORDINALITY AS k(attnum, ordinality)
        JOIN pg_attribute a
          ON a.attrelid = con.conrelid
         AND a.attnum = k.attnum
    ) AS columns,
    pg_get_constraintdef(con.oid, true) AS definition
FROM pg_constraint con
JOIN pg_class tbl ON tbl.oid = con.conrelid
JOIN pg_namespace n ON n.oid = tbl.relnamespace
WHERE con.contype IN ('p', 'u', 'f')
  AND tbl.relname = 'rappels_whatsapp'
  AND EXISTS (
      SELECT 1
      FROM information_schema.tables t
      WHERE t.table_schema = n.nspname
        AND t.table_name = tbl.relname
        AND t.table_type = 'BASE TABLE'
  )
ORDER BY n.nspname, con.conname;

-- 6. Index connus sur rappels_whatsapp ; zero ligne si la table est absente.
SELECT i.schemaname AS table_schema, i.indexname, i.indexdef
FROM pg_indexes i
WHERE i.tablename = 'rappels_whatsapp'
  AND EXISTS (
      SELECT 1
      FROM information_schema.tables t
      WHERE t.table_schema = i.schemaname
        AND t.table_name = i.tablename
        AND t.table_type = 'BASE TABLE'
  )
ORDER BY i.schemaname, i.indexname;
