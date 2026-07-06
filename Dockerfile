# ---------- Étape 1 : build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# On copie d'abord le pom.xml pour profiter du cache Docker sur les dépendances
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Puis le code source
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Étape 2 : image finale ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Utilisateur non-root pour la sécurité
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:${SERVER_PORT:-8080}/api/notifications/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
