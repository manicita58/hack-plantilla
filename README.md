# hack-plantilla

Plantilla de proyecto para el VPS: **Spring Boot 4.1 + Java 25 + PostgreSQL 18**
+ front estático, lista para desplegar detrás del Traefik compartido de `/opt/edge`.

Verificada de punta a punta: compila, Flyway migra, JPA valida el esquema, el CRUD
escribe y lee, Swagger responde, CORS deja pasar los orígenes permitidos y bloquea
el resto, y el test pasa.

```
apps/api/                 backend Spring Boot
  src/main/java/…         Application, Item, ItemRepository, ItemController, CorsConfig
  src/main/resources/
    application.yml       config; /health en la raíz, no en /actuator
    db/migration/         migraciones Flyway (V1__init.sql, V2__…, …)
  Dockerfile              multi-stage: build con JDK, runtime con JRE
apps/web/index.html       front: un archivo, sin build step ni dependencias
docker-compose.prod.yml   producción: api + db, SIN proxy (es del server)
docker-compose.yml        desarrollo local: solo la base
.github/workflows/        test -> build -> deploy
```

| URL | Qué es |
|---|---|
| `/health` | estado, lo miran el healthcheck de Docker y Traefik |
| `/items` | CRUD de ejemplo (GET, POST) |
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | el OpenAPI en JSON |

## Desarrollo local

```bash
docker compose up -d                    # levanta Postgres en :5432
cd apps/api && mvn spring-boot:run
```

```bash
curl localhost:8080/health
curl -X POST localhost:8080/items -H 'Content-Type: application/json' -d '{"name":"hola"}'
curl localhost:8080/items
xdg-open http://localhost:8080/swagger-ui.html
```

Y el front, en otra terminal:

```bash
cd apps/web && python3 -m http.server 5500
xdg-open http://127.0.0.1:5500
```

El front detecta `localhost` y apunta solo a `http://localhost:8080`. Ese origen
ya viene en el default de `app.cors-origins`, así que funciona sin configurar nada.

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
#   CORS_ORIGINS=https://tudominio.com,https://www.tudominio.com
```

> `CORS_ORIGINS` tiene que listar el dominio **del front**, no el de la API. Si
> falta, el back responde bien por `curl` y el front falla con un error de CORS
> que en el browser parece "no hay conexión".

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

Y abrí `https://api.tudominio.com/swagger-ui.html` en el browser.

De acá en más, cada push a `main` que toque `apps/api/**` despliega solo.

### 9. El front, en Cloudflare Pages

El front **no va al VPS**: es estático, lo sirve Cloudflare gratis desde su CDN.

1. Cloudflare → **Workers & Pages** → **Create** → **Pages** → conectá el repo.
2. **Root directory:** `apps/web`
3. **Build command:** vacío — no hay build, es un `index.html`.
4. **Output directory:** `.` (el mismo `apps/web`)
5. **Custom domain:** `tudominio.com` (y `www`), que quedan **proxied (naranja)**.

El front deduce la URL del back del dominio: si está en `tudominio.com`, pega a
`https://api.tudominio.com`. Si tu setup no sigue esa convención, cambiá el
`API_BASE` que está al principio del `<script>`.

Cada push a `main` redespliega el front solo, sin tocar nada del VPS. Esa es toda
la "CI del front": Cloudflare la maneja, no hace falta workflow.

> Si preferís el front **también** en el VPS (SSR real con Next/Nuxt, por
> ejemplo), agregás un servicio `web` al compose con su propio router. Está
> documentado en `/opt/edge/NUEVO-PROYECTO.md`. Perdés el CDN y sumás RAM.

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

**`springdoc-openapi` 3.x, no 2.x.** La línea 2.x es para Spring Boot 3. Con Boot
4 hay que usar la 3.x o el arranque falla.

**CORS se configura en el back, no en el front.** El browser bloquea la respuesta
antes de que el JS la vea, y el error que reporta (`TypeError: Failed to fetch`)
no dice nada útil. Si el front no carga datos pero `curl` a la API anda, mirá
`CORS_ORIGINS`.
