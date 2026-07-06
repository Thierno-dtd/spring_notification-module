.PHONY: help env build run test clean docker-build up down logs ps restart rebuild

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

env: ## Crée le .env à partir du template s'il n'existe pas encore
	@test -f .env || cp .env.example .env
	@echo ".env prêt (vérifie/complète les valeurs sensibles avant de lancer)"

build: ## Compile le projet avec Maven (sans les tests)
	./mvnw clean package -DskipTests

test: ## Lance les tests
	./mvnw test

run: env ## Lance l'application en local (hors Docker), nécessite Postgres/Redis démarrés
	./mvnw spring-boot:run

clean: ## Nettoie les artefacts de build
	./mvnw clean

docker-build: ## Construit l'image Docker de l'application
	docker compose build

up: env ## Démarre toute la stack (app + postgres + redis)
	docker compose up -d

down: ## Arrête et supprime les conteneurs
	docker compose down

logs: ## Affiche les logs de l'application en continu
	docker compose logs -f app

ps: ## Liste les conteneurs du projet
	docker compose ps

restart: ## Redémarre uniquement le service app
	docker compose restart app

rebuild: ## Reconstruit l'image et relance tout proprement
	docker compose down
	docker compose build --no-cache
	docker compose up -d
