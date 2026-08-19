# Atajos de desarrollo local. `make` sin argumentos lista lo que hay.
API := http://localhost:8080

.DEFAULT_GOAL := help
.PHONY: help dev db front test seed verify break reset

help:  ## muestra esta ayuda
	@awk 'BEGIN{FS=":.*## "} /^[a-z][a-z-]*:.*## /{printf "  make %-7s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

dev: db  ## base + API + front, todo en http://localhost:8080
	cd apps/api && mvn -q spring-boot:run

db:  ## solo la base, y espera a que acepte conexiones
	docker compose up -d --wait

front:  ## el front aparte en 127.0.0.1:5500, para probar el CORS de verdad
	cd apps/web && python3 -m http.server 5500 --bind 127.0.0.1

test: db  ## corre los tests (necesitan la base arriba)
	cd apps/api && mvn -q test

seed:  ## mete tres bloques de ejemplo por la API
	@for n in uno dos tres; do curl -s -X POST $(API)/items -H 'Content-Type: application/json' -d "{\"name\":\"$$n\"}" > /dev/null; done
	@$(MAKE) --no-print-directory verify

verify:  ## estado de la cadena
	@curl -s $(API)/chain/verify; echo

break:  ## adultera el bloque 1 por fuera de la API: la cadena debe romperse
	@docker compose exec -T db psql -qU hack -d hack -c "UPDATE items SET name='adulterado' WHERE id=1" > /dev/null
	@$(MAKE) --no-print-directory verify

reset:  ## tira la base y el volumen: se arranca de cero
	docker compose down -v
