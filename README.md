# hack-plantilla

Plantilla de proyecto para el VPS: **Spring Boot 4.1 + Java 25 + PostgreSQL 18**
+ front estático, lista para desplegar detrás del Traefik compartido de `/opt/edge`.

Verificada de punta a punta: compila, Flyway migra, JPA valida el esquema, el CRUD
escribe y lee, Swagger responde, CORS deja pasar los orígenes permitidos y bloquea
el resto, y el test pasa.

```
apps/api/                 backend Spring Boot
  src/main/java/…         Application, Item, ItemRepository, ItemController,
                          Chain (cadena de bloques), ChainController, CorsConfig
  src/main/resources/
    application.yml       config; /health en la raíz, no en /actuator
    db/migration/         migraciones Flyway (V1__init.sql, V2__…, …)
  Dockerfile              multi-stage: build con JDK, runtime con JRE
apps/web/index.html       front: un archivo, sin build step ni dependencias
apps/web/blockchain.svg   ícono del bloque (lucide `boxes`, ISC); va inline en el html
docker-compose.prod.yml   producción: api + db, SIN proxy (es del server)
docker-compose.yml        desarrollo local: solo la base
.github/workflows/        test -> build -> deploy
```

| URL | Qué es |
|---|---|
| `/health` | estado, lo miran el healthcheck de Docker y Traefik |
| `/items` | CRUD de ejemplo (GET, POST) — cada POST agrega un bloque |
| `/chain/verify` | recalcula la cadena: `{valid, blocks, brokenAt}` |
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
curl localhost:8080/chain/verify
xdg-open http://localhost:8080/swagger-ui.html
```

## La cadena

Cada item es un bloque: guarda `hash` = SHA-256(`prevHash` + nombre + fecha) y el
`prevHash` del anterior. Tocar una fila vieja rompe todos los hashes que le siguen,
y `/chain/verify` devuelve en qué bloque se rompió. El front lo muestra en el
escudo de verificado, que se recalcula al cargar y al agregar (o al hacerle clic).
Cada item lleva el ícono del bloque: al clickearlo abre su "contrato" — contenido,
fecha sellada, hash, hash anterior, la fórmula y si ese bloque quedó dentro de la
parte rota de la cadena.

Probalo rompiéndola a mano:

```bash
docker compose exec db psql -U hack -d hack -c \
  "UPDATE items SET name='adulterado' WHERE id=1"
curl localhost:8080/chain/verify   # -> {"valid":false,"blocks":N,"brokenAt":1}
```

No hay red, ni consenso, ni wallets: es un ledger a prueba de manipulación dentro
de tu propio Postgres. Las filas creadas antes de la migración `V2` no son bloques
y la verificación las ignora.

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

## Cambiar de dominio

El dominio vive en **tres lugares**, ninguno de ellos el código del front. Reemplazá
`nuevo.com` por el que va.

### 1. Zona en Cloudflare

**Add site** `nuevo.com` → apuntá los nameservers en tu registrador → esperá a que la
zona quede `Active`. Si el dominio ya estaba en Cloudflare, este paso no existe.

### 2. DNS: solo el record de la API

| Name | Type | Content | Proxy |
|---|---|---|---|
| `api` | A | IP del VPS | **DNS only** (gris) |

Gris, no naranja. En naranja Cloudflare intercepta el 443, Let's Encrypt nunca valida
y Traefik sirve `TRAEFIK DEFAULT CERT` para siempre.

Los records de `nuevo.com` y `www.nuevo.com` **no se crean acá**: los hace Pages en el
paso 4.

### 3. VPS: `.env` y recrear el contenedor

```bash
cd /opt/hack
sd 'API_DOMAIN=.*'   'API_DOMAIN=api.nuevo.com' .env
sd 'CORS_ORIGINS=.*' 'CORS_ORIGINS=https://nuevo.com,https://www.nuevo.com' .env

docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps api
docker compose -f docker-compose.prod.yml logs -f api
```

`--force-recreate` **no es opcional**: las labels de Traefik se resuelven desde
`${API_DOMAIN}` en el momento de crear el contenedor. Un `restart` conserva las viejas
y el router sigue publicando el dominio anterior, sin dar ningún error.

Corré esto **después** de que `api.nuevo.com` resuelva. Al revés, Traefik pide el
certificado antes de que exista el DNS, Let's Encrypt falla y gastás reintentos del
rate limit (5 fallos por hora por dominio).

### 4. Cloudflare Pages

**Workers & Pages → el proyecto → Custom domains**: agregá `nuevo.com` y
`www.nuevo.com`, y borrá los viejos con el `...`. Cloudflare crea y elimina los
records DNS solo.

### 5. El front: nada

`apps/web/index.html` arma la URL del back con `https://api.` + el hostname sin `www.`.
Mientras mantengas la convención `api.<dominio>`, se adapta solo. Si la rompés, ahí sí
editás el `API_BASE` al principio del `<script>`.

### Verificar

```bash
dig @1.1.1.1 +short api.nuevo.com nuevo.com
curl -sI https://api.nuevo.com/health                  # 200
curl -sI https://nuevo.com                             # 200
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Origin: https://nuevo.com' https://api.nuevo.com/items   # 200, no 403
```

El último es el que más se olvida. Si da `403`, quedó mal `CORS_ORIGINS` — y en el
browser eso se ve como "el front no carga nada", sin ningún error que lo diga.

## Diagnosticar un deploy que "no anda"

En orden, de afuera hacia adentro. El primero que falle es la causa.

```bash
# 1. ¿Existe el nombre? Consultá al nameserver autoritativo, no a tu resolver.
dig @jack.ns.cloudflare.com +short api.tudominio.com

# 2. ¿Responde y con qué certificado?
curl -sI https://api.tudominio.com/health
echo | openssl s_client -connect api.tudominio.com:443 \
  -servername api.tudominio.com 2>/dev/null | openssl x509 -noout -subject -issuer -dates

# 3. En el VPS: ¿los contenedores están arriba y sanos?
cd /opt/hack
docker compose -f docker-compose.prod.yml ps          # ojo con `Restarting (1)`
docker compose -f docker-compose.prod.yml logs --tail=50 api

# 4. ¿Existe la red del proxy compartido?
docker network ls | rg edge

# 5. ¿El .env tiene todo?
rg -N 'API_DOMAIN|GHCR_REPO|PGDATA_DIR|CORS_ORIGINS' .env
```

| Síntoma | Causa casi siempre |
|---|---|
| `DNS_PROBE_POSSIBLE` en el browser | el record no existe; mirá si le pusiste `www` o no |
| `TRAEFIK DEFAULT CERT` | el record de `api` quedó **naranja**; ponelo gris |
| 522 en el dominio del front | el dominio tiene DNS pero no está dado de alta en Pages |
| `Restarting (1)` en `db` | mount de Postgres 18 en `.../data` en vez de `/var/lib/postgresql` |
| `Schema validation: missing table` | falta `spring-boot-starter-flyway`; Flyway no corrió |
| el front no carga datos pero `curl` a la API anda | `CORS_ORIGINS` no lista el dominio del front |
| pull falla con `denied` | el token de GHCR es fine-grained; tiene que ser **classic** |

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

**`forward-headers-strategy: framework`.** Traefik termina el TLS y le pasa HTTP
plano a la app. Sin esto Spring cree que la petición vino por HTTP y genera todas
las URLs absolutas con `http://` — el Swagger queda con un server `http://` y el
"Try it out" falla por mixed content. Afecta también a redirects y `Location`.

**Postgres 18 monta en `/var/lib/postgresql`, no en `.../data`.** Desde la 18 la
imagen guarda los datos en un subdirectorio versionado (`18/docker`) para poder
hacer `pg_upgrade --link`. Con el mount viejo el contenedor entra en crash loop y
el `up -d` **no da error**: solo lo ves como `Restarting (1)` en `docker ps`.

**CORS se configura en el back, no en el front.** El browser bloquea la respuesta
antes de que el JS la vea, y el error que reporta (`TypeError: Failed to fetch`)
no dice nada útil. Si el front no carga datos pero `curl` a la API anda, mirá
`CORS_ORIGINS`.

**Un deploy en verde no significa desplegado.** El job `deploy` corre
`up -d --force-recreate --no-deps api`. `--no-deps` hace que toque **solo** `api`: no
levanta `db`, no evalúa el `depends_on: service_healthy` y no espera al healthcheck.
Si la base nunca se levantó a mano, la API entra en crash-loop y **el workflow sale
igual en verde**. Verde quiere decir "la imagen se publicó y el contenedor se
recreó", nada más. Lo desplegado se comprueba con `curl` al `/health` público.

**Los custom domains de Pages se dan de alta en Pages, no en DNS.** Crear el record a
mano en la pantalla de DNS no alcanza: sin el dominio registrado en el proyecto,
Cloudflare no le emite certificado ni sabe a qué proyecto rutearlo, y devuelve 522.
Se hace al revés — lo agregás en **Custom domains** y Cloudflare crea el record solo.

**El dominio raíz del front va como CNAME, no como A.** Pages no te da una IP fija, así
que el apex apunta por CNAME a `<proyecto>.pages.dev` (Cloudflare lo aplana solo, que
es lo que hace legal un CNAME en el apex). Y va **proxied (naranja)**, al revés que
`api`, que necesita quedar gris para Let's Encrypt.

**Un `NXDOMAIN` te queda cacheado 30 minutos.** El SOA de la zona declara un TTL
negativo de 1800s: si consultaste un nombre *antes* de crear el record, tu resolver
recuerda que no existía durante media hora, aunque ya esté publicado. Para ver la
verdad al instante, preguntale al nameserver autoritativo:
`dig @jack.ns.cloudflare.com +short www.tudominio.com`.
