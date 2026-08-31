# ============================================================
#  Dockerfile — manao-backend (Spring Boot 3.3 / Java 21)
#  Build multi-stage : compile avec Maven, exécute avec un JRE léger.
# ============================================================

# ---- Étape 1 : build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copier uniquement le pom.xml d'abord pour profiter du cache Docker
# sur les dépendances tant que le pom.xml ne change pas.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copier le reste des sources et builder le jar (tests exclus pour un build CD rapide,
# les tests sont déjà exécutés par la CI GitHub Actions).
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Étape 2 : image d'exécution ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Utilisateur non-root pour la sécurité
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/backend.jar app.jar

# Render fournit dynamiquement la variable $PORT : on force Spring Boot
# à écouter dessus, indépendamment de SERVER_PORT défini par ailleurs.
EXPOSE 4000

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-4000}"]
