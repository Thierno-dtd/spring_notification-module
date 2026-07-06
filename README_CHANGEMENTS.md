# Fichiers modifiés / ajoutés — notification-module (branche thierno)

Extrais cette archive à la racine de ton repo (elle respecte l'arborescence
`src/main/java/...`), en écrasant les fichiers existants.

## Fichiers MODIFIÉS
- `src/main/java/.../dto/NotificationTemplateDto.java` — exemples Swagger clairs (`{{variable}}`), `id` n'est plus obligatoire dans le body.
- `src/main/java/.../services/servicesImpl/NotificationTemplateService.java` — fusionne les `variables` envoyées manuellement avec celles détectées automatiquement au lieu de les écraser.
- `src/main/java/.../controllers/*.java` (5 fichiers) — suppression de `@CrossOrigin(origins = "*")` codé en dur, remplacé par la config CORS centralisée.
- `src/main/resources/application.properties` — entièrement paramétré via variables d'environnement (`${VAR:defaut}`).

## Fichiers AJOUTÉS
- `src/main/java/.../config/CorsProperties.java` et `CorsConfig.java` — CORS piloté par `CORS_ORIGINS`.
- `.env.example` — à copier en `.env` puis à compléter.
- `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `Makefile` — outillage de déploiement.

## Démarrage rapide
```bash
make env      # crée .env depuis .env.example
# -> édite .env : mets ton vrai DB_PASSWORD, active EMAIL/SMS/PUSH si besoin
make up       # build + démarre app + postgres + redis
make logs     # suit les logs de l'app
```

Swagger UI : http://localhost:8080/swagger-ui.html
