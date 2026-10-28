# Phase 2 — corrections groupes 1 à 7 (NON compilé / NON testé par l'auteur)

> Maven Central était inaccessible dans l'environnement de travail : **aucun test n'a été exécuté**.
> Seule une analyse syntaxique `javac` (parse) a réussi. À vous de lancer les tests (ci-dessous).

## 1. Lancer les tests (PowerShell, Windows)
```powershell
cd chemin\vers\serveur_springboot-phase2
java -version          # attendu : 21
mvn -v
mvn -B clean verify    # compile + tous les tests
# Un seul test :
mvn -B -Dtest=PaiementServiceTest test
```
Si `mvn` échoue avec « Non-resolvable parent POM … Unknown host » : c'est le **réseau** (accès à repo.maven.apache.org),
pas le code. Vérifiez la connexion / le proxy (`%USERPROFILE%\.m2\settings.xml`) et relancez.

## 2. Migrations SQL (NON exécutées) — ordre, sur une base de TEST d'abord
1. `database_migration_auth_rate_limit.sql` (phase 1)
2. `database_migration_jwt_invalidation.sql`
3. `database_migration_rappels_whatsapp.sql`
4. `database_migration_places_unicite.sql` — **lancer d'abord les pré-contrôles (section 0) : ils doivent retourner 0 ligne.**
Appliquer les colonnes/tables (1 à 3) AVANT de déployer le code (`ddl-auto=validate`).

## 3. Variables d'environnement (valeurs fictives)
```
SEED_DEMO_ENABLED=false            # true = comptes de démo (dev local uniquement)
RAPPEL_WHATSAPP_RETRY_MINUTES=5
EVOLUTION_ENABLED=false            # true seulement si Evolution API est configuré
EVOLUTION_API_URL=https://evolution.exemple.invalid
EVOLUTION_API_KEY=<clé-fictive>
EVOLUTION_INSTANCE=<instance>
```

## 4. Adaptations frontend nécessaires (non faites, hors périmètre)
- `PaiementClient.jsx` : ne plus envoyer `statut: "Réussi"` ; afficher « En attente de confirmation » ; ne plus appeler `createRecu` juste après le paiement (il renvoie 409 tant que paiement non confirmé ET réservation non validée).
- `VerificationQR.jsx` : `POST /recus/verify/{token}/checkin` exige maintenant un jeton (Président de la coopérative ou ADMIN) ; `GET /recus/verify/{token}` reste public mais renvoie une vue minimale (nom/téléphone masqués, sans montant ni jeton).
- Création manuelle `POST /reservations` : `numero_place` est désormais obligatoire.
- Voyages : capacité 1–100, prix 0–10 000 000, statut actif/complet/annulé, date non passée.
