# hack-plantilla

Plantilla de proyecto para el VPS: **Spring Boot 4.1 + Java 25 + PostgreSQL 18**,
lista para desplegar detrás del Traefik compartido de `/opt/edge`.

Verificada de punta a punta: compila, Flyway migra, JPA valida el esquema, el
CRUD escribe y lee, y el test pasa.

```
apps/api/                 backend Spring Boot
  src/main/java/…         Application, Item, ItemRepository, ItemController
  src/main/resources/
    application.yml       config; /health en la raíz, no en /actuator
    db/migration/         migraciones Flyway (V1__init.sql, V2__…, …)
  Dockerfile              multi-stage: build con JDK, runtime con JRE
docker-compose.prod.yml   producción: api + db, SIN proxy (es del server)
docker-compose.yml        desarrollo local: solo la base
.github/workflows/        test -> build -> deploy
```

## Desarrollo local

```bash
docker compose up -d                    # levanta Postgres en :5432
cd apps/api && mvn spring-boot:run

curl localhost:8080/health
curl -X POST localhost:8080/items -H 'Content-Type: application/json' -d '{"name":"hola"}'
curl localhost:8080/items
```

## Desplegarlo — paso a paso

Reemplazá `hack` por el nombre real del proyecto y `tudominio.com` por el tuyo.
El servidor es el de `server-edge`; si es nuevo, corré antes `/opt/edge/bootstrap.sh`.

### 1. Repo en GitHub

```bash
cd ~/Documents/repos/hack-plantilla
git init && git add -A && git commit -m "feat: plantilla spring + postgres"
gh repo create OWNER/hack --private --source=. --push
```

### 2. DNS en Cloudflare

`api.tudominio.com` → **A** → IP del VPS → **DNS only (nube gris)**.

> En naranja Cloudflare intercepta el 443 y Let's Encrypt nunca valida: Traefik
> se queda sirviendo `TRAEFIK DEFAULT CERT` para siempre.

### 3. Secrets del repo

`Settings → Secrets and variables → Actions`:

| Secret | Valor |
|---|---|
| `VPS_HOST` | IP del VPS |
| `VPS_USER` | `root` |
| `VPS_SSH_KEY` | clave privada OpenSSH completa (`BEGIN`/`END` incluidos) |

Son los mismos del resto de proyectos del server: la llave de Actions → VPS es
una sola para todos.

### 4. Deploy key (en el VPS)

GitHub no deja reusar una deploy key en dos repos, y `~/.ssh/config` usa
`IdentitiesOnly`. Cada repo necesita llave propia **y alias de Host**:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/hack_deploy -N "" -C "vps-hack"
cat ~/.ssh/hack_deploy.pub        # -> repo > Settings > Deploy keys (read-only)

printf '\nHost github-hack\n  HostName github.com\n  IdentityFile ~/.ssh/hack_deploy\n  IdentitiesOnly yes\n' >> ~/.ssh/config
ssh -T git@github-hack            # debe decir: Hi OWNER/hack!
```

### 5. Clonar y configurar (en el VPS)

```bash
git clone git@github-hack:OWNER/hack.git /opt/hack
mkdir -p /srv/hack/pgdata

cd /opt/hack && cp env.example .env && vim .env
#   API_DOMAIN=api.tudominio.com
#   GHCR_REPO=OWNER/hack/api
#   POSTGRES_PASSWORD=$(openssl rand -hex 24)
```

### 6. Login a GHCR (una vez por server, no por proyecto)

```bash
read -rs T && echo "$T" | docker login ghcr.io -u OWNER --password-stdin && unset T
```

> Tiene que ser un token **classic** con `read:packages`. Los fine-grained dan
> `Login Succeeded` y después fallan el pull con `denied`.

### 7. Primer arranque

El primer push a `main` construye y publica la imagen. Después, en el VPS:

```bash
cd /opt/hack
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

No hay paso de migraciones: **Flyway corre solo al arrancar la app**.

### 8. Verificar

```bash
curl -sI https://api.tudominio.com/health      # 200 — dale ~60s al certificado
curl -X POST https://api.tudominio.com/items -H 'Content-Type: application/json' -d '{"name":"ok"}'
```

De acá en más, cada push a `main` que toque `apps/api/**` despliega solo.

## Detalles que no son obvios

**`spring-boot-starter-flyway`, no `flyway-core`.** En Spring Boot 4 las
autoconfiguraciones se partieron en módulos por tecnología. Con `flyway-core` a
secas la app levanta, Flyway **no corre nunca**, y Hibernate muere con
`Schema validation: missing table`.

**`-XX:MaxRAMPercentage=70` en el Dockerfile.** La JVM por defecto ignora el
límite de memoria del contenedor y se cree dueña de toda la RAM del host → el
OOM-killer se la lleva puesta. El flag la ata al límite real.

**`start_period: 60s` en el healthcheck.** La JVM tarda en arrancar. Sin eso el
contenedor queda `unhealthy` antes de terminar de levantar y Docker lo reinicia
en loop.

**`ddl-auto: validate`.** El esquema lo maneja Flyway. Hibernate solo verifica
que coincida con las entidades y avisa si se desincronizan, en vez de mutar
tablas por su cuenta.

**Nombres de Traefik prefijados** (`hack-api`, `hack-ratelimit`). El namespace es
global en todo el server: dos proyectos con un router `api` se pisan en silencio.

**640MB de límite para la API.** Una JVM necesita más aire que un runtime
interpretado. Es un techo, no una reserva.
