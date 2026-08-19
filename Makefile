# Atajos de desarrollo local. `make` sin argumentos lista lo que hay.
API := http://localhost:8080

.DEFAULT_GOAL := help
.PHONY: help dev web db install test seed verify break ai geo reset

help:  ## muestra esta ayuda
	@awk 'BEGIN{FS=":.*## "} /^[a-z][a-z-]*:.*## /{printf "  make %-8s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

dev: db  ## base + API en http://localhost:8080 (el front va aparte: make web)
	cd apps/api && mvn -q spring-boot:run

web: install  ## front Angular en http://localhost:4200, con recarga en caliente
	cd apps/web && npm start

db:  ## solo la base (Postgres + PostGIS + pgvector), y espera a que responda
	docker compose up -d --wait

install:  ## dependencias del front (solo la primera vez)
	@test -d apps/web/node_modules || (cd apps/web && npm install)

test: db  ## tests del back (los de dominio no necesitan base; los de módulo sí)
	cd apps/api && mvn -q test

seed:  ## mete tres bloques de ejemplo en la cadena
	@for n in uno dos tres; do curl -s -X POST $(API)/ledger -H 'Content-Type: application/json' -d "{\"content\":\"$$n\"}" > /dev/null; done
	@$(MAKE) --no-print-directory verify

verify:  ## estado de la cadena
	@curl -s $(API)/ledger/verify; echo

break:  ## adultera el bloque 1 por fuera de la API: la cadena debe romperse
	@docker compose exec -T db psql -qU hack -d hack -c "UPDATE ledger_entries SET content='adulterado' WHERE id=1" > /dev/null
	@$(MAKE) --no-print-directory verify

ai:  ## estado del módulo de IA (modelo, key, documentos indexados)
	@curl -s $(API)/ai/status; echo

geo:  ## features cargadas en el geovisor, por categoría
	@curl -s $(API)/geo/stats; echo

reset:  ## tira la base y el volumen: se arranca de cero
	docker compose down -v
