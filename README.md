# hack-plantilla

Plantilla para hackatones: **Spring Boot 4.1 + Java 25 + PostgreSQL 18 + Angular 21**,
lista para desplegar detrás del Traefik compartido de `/opt/edge`.

Trae tres módulos armados y verificados. Cada uno se prende o apaga por variable
de entorno, y se arranca sin él borrando una carpeta.

| Módulo | Qué trae | Endpoints | Apagar con |
|---|---|---|---|
| **Cadena de bloques** | Ledger encadenado por hash: cada entrada sella la anterior y `verify` detecta cualquier manipulación | `/ledger`, `/ledger/verify` | `MODULE_BLOCKCHAIN=false` |
| **IA** | Chat con streaming token a token e historial, más RAG (ingesta, embeddings y búsqueda semántica en pgvector) contra DeepInfra | `/ai/chat`, `/ai/ask`, `/ai/documents`, `/ai/status` | `MODULE_AI=false` |
| **Geovisor** | Mapa Leaflet sobre PostGIS: consulta por bbox, búsqueda por cercanía en metros y GeoJSON de ida y de vuelta | `/geo/features`, `/geo/features/near`, `/geo/stats` | `MODULE_GEO=false` |

Y lo de siempre: `/health`, Swagger en `/swagger-ui.html`, OpenAPI en `/v3/api-docs`,
migraciones Flyway, CORS por `.env` y CI/CD a GitHub Actions.

## Arquitectura

Un paquete por módulo, y adentro de cada uno un hexágono:

```
com.hackplantilla
├── shared/                  CORS y formato de errores; lo único transversal
├── blockchain/
│   ├── domain/              Block, ChainHasher, ChainVerifier, BlockRepository (puerto)
│   ├── application/         LedgerService — los casos de uso
│   └── infrastructure/      JPA (persistence) y REST (web) — los adaptadores
├── ai/                      misma forma: ChatModel/EmbeddingModel/ChunkStore son puertos,
│                            DeepInfra y pgvector son adaptadores
└── geo/                     misma forma: FeatureRepository es puerto, PostGIS es adaptador
```

Tres consecuencias prácticas:

- **El dominio no sabe de Spring.** `ChainVerifierTest` y `TextChunkerTest` corren
  sin contexto, sin base y sin red, en milisegundos.
- **Cambiar de proveedor es cambiar un adaptador.** DeepInfra habla el protocolo
  de OpenAI: apuntando `DEEPINFRA_BASE_URL` a OpenRouter, Together, vLLM o un
  Ollama local, no se toca una línea de `application/` ni de `domain/`.
- **Los módulos no se conocen entre sí.** Nada fuera de `ai/` importa `ai/`.

### Desprender un módulo

Tres pasos, sin tocar nada más:

1. Borrá el paquete (`apps/api/src/main/java/com/hackplantilla/<modulo>/`) y su test.
2. Borrá su migración (`V4__ai.sql` o `V5__geo.sql`).
3. Borrá su carpeta en `apps/web/src/app/features/` y su ruta en `app.routes.ts`.

Si solo lo querés apagado (para una demo, o porque no tenés la API key), alcanza
con `MODULE_AI=false` en el `.env`: los beans no se registran y los endpoints
devuelven 404. Las migraciones corren igual — por eso el paso 2 existe.

En el front cada módulo es una ruta con `loadComponent`, o sea un chunk aparte:
sacarlo también achica lo que se baja el browser.

### Agregar un módulo nuevo

Los tres que vienen son ejemplos de la misma forma. Para el cuarto, copiá la
estructura de `geo/` (es la más chica) y seguí estos pasos.

**1. El paquete**, en `apps/api/src/main/java/com/hackplantilla/<modulo>/`:

```
<modulo>/
  <Modulo>Module.java          la meta-anotación del flag (copiá GeoModule.java)
  domain/                      records y interfaces. Sin Spring, sin JPA, sin HTTP
  application/                 @Service con los casos de uso, hablando con los puertos
  infrastructure/
    persistence/               el adaptador de datos (JPA o JdbcTemplate)
    web/                       @RestController y sus DTOs
```

La meta-anotación son ocho líneas y es lo que hace el módulo apagable:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "modules.<modulo>", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public @interface <Modulo>Module { }
```

Después va en cada bean del módulo: `@Service @<Modulo>Module`, `@Repository
@<Modulo>Module`, `@RestController @<Modulo>Module`.

**2. La migración**: `V6__<modulo>.sql`. Nunca se edita una ya aplicada; siempre
va una nueva.

**3. La config**, en `application.yml`:

```yaml
modules:
  <modulo>:
    enabled: ${MODULE_<MODULO>:true}
```

**4. El front**: carpeta en `apps/web/src/app/features/<modulo>/` con su
`*.service.ts` (que usa `Api` de `core/`) y su página, más la ruta:

```ts
{ path: '<modulo>', loadComponent: () => import('./features/<modulo>/<modulo>-page')
                                          .then((m) => m.<Modulo>Page) },
```

y el link en `app.html`.

**5. Los tests**: uno de dominio sin Spring (rápido, es el que vas a correr todo
el tiempo) y uno de integración `@SpringBootTest @Transactional` si toca la base.

Checklist de que quedó bien desprendible:

- [ ] Ningún archivo fuera de `<modulo>/` lo importa.
- [ ] `MODULE_<MODULO>=false` arranca la app y sus endpoints dan 404.
- [ ] Borrar la carpeta + la migración + la ruta del front deja todo compilando.

## Estructura

```
apps/api/                 backend Spring Boot
  src/main/java/…         shared/ + blockchain/ + ai/ + geo/ (ver Arquitectura)
  src/main/resources/
    application.yml       config, flags de módulos y parámetros de IA
    db/migration/         V1..V5 — Flyway las corre al arrancar
  Dockerfile              multi-stage: build con JDK, runtime con JRE
apps/web/                 front Angular 21 (standalone, signals, zoneless)
  src/app/core/           cliente HTTP y URL de la API
  src/app/features/       un módulo = una carpeta = una ruta lazy
docker/postgres/          Postgres 18 + PostGIS + pgvector (no hay imagen oficial con ambas)
Makefile                  atajos: make dev / web / seed / break / ai / geo
docker-compose.prod.yml   producción: api + db, SIN proxy (es del server)
docker-compose.yml        desarrollo local: solo la base
.github/workflows/        test -> build -> deploy
```

| URL | Qué es |
|---|---|
| `/health` | estado, lo miran el healthcheck de Docker y Traefik |
| `/ledger` | la cadena (GET) y sellar un bloque (POST) |
| `/ledger/verify` | recalcula la cadena: `{valid, blocks, brokenAt}` |
| `/ai/status` | si el módulo está configurado, qué modelo usa y qué documentos hay |
| `/ai/chat` | chat con streaming (SSE), POST con `{conversationId?, message}` |
| `/ai/documents` | indexar (POST), listar (GET) y borrar (DELETE) documentos del RAG |
| `/ai/ask` | pregunta contra los documentos indexados, con las fuentes que usó |
| `/geo/features` | GeoJSON filtrado por `bbox` y `category` |
| `/geo/features/near` | lo que está a `meters` de `lon`/`lat`, ordenado por distancia |
| `/geo/stats` | conteo de features por categoría |
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | el OpenAPI en JSON |

## Desarrollo local

Necesitás Java 25, Docker, Maven (`sudo dnf install maven`) y Node 20.19+/22.12+.

```bash
make            # lista los atajos
make dev        # base + API en http://localhost:8080
make web        # front Angular en http://localhost:4200 (otra terminal)
```

`make dev` levanta el Postgres del proyecto (con PostGIS y pgvector), espera a
que acepte conexiones y recién ahí arranca Spring. `make web` instala las
dependencias la primera vez y deja `ng serve` con recarga en caliente.

| Atajo | Qué hace |
|---|---|
| `make dev` | base + API |
| `make web` | front Angular con hot reload |
| `make seed` | sella tres bloques de ejemplo |
| `make verify` | estado de la cadena |
| `make break` | adultera el bloque 1 con SQL: la cadena tiene que romperse |
| `make ai` | estado del módulo de IA (modelo, key, documentos) |
| `make geo` | features cargadas, por categoría |
| `make test` | tests del back |
| `make reset` | borra base y volumen, se arranca de cero |

El front local (`:4200`) le pega a la API en `:8080`; ese origen ya viene en el
default de `app.cors-origins`, así que funciona sin configurar nada. Después de
`npm run build` en `apps/web`, Spring también sirve el front compilado desde
`http://localhost:8080` — mismo origen, sin CORS, útil para demos.

## Proteger lo que cuesta plata

Dos capas, las dos apagadas por defecto para que la demo funcione sin configurar
nada:

**Token compartido** — con `API_TOKEN` en el `.env`, todo `/ai/**` y las
escrituras de `/geo` piden el header `X-Api-Token`. El resto (leer el mapa, la
cadena, `/health`) sigue abierto. En el front se guarda a mano desde la consola:

```js
localStorage.setItem('apiToken', 'el-token')
```

No se hardcodea en el bundle: un token que viaja en un front público lo lee
cualquiera en el devtools. Sirve para que un bot no te encuentre el endpoint y te
queme los créditos, **no** para autenticar usuarios — para eso hace falta login
de verdad.

**Rate limit por IP** — las labels de Traefik en `docker-compose.prod.yml` le dan
a `/ai` un router propio con 20 requests por minuto por IP, en vez de los 50 por
segundo del resto. Esa es la capa que sí protege contra abuso desde el browser,
donde no hay secreto posible.

Y un tope de tamaño: `/ai/documents` rechaza textos de más de
`AI_MAX_DOCUMENT_CHARS` (200k, unas 100 páginas), porque un documento gigante son
miles de llamadas de embedding en un solo request.

## La cadena

Cada item es un bloque: guarda `hash` = SHA-256(`prevHash` + nombre + fecha) y el
`prevHash` del anterior. Tocar una fila vieja rompe todos los hashes que le siguen,
y `/ledger/verify` devuelve en qué bloque se rompió. El front lo muestra en el
escudo de verificado, que se recalcula al cargar y al agregar (o al hacerle clic).
Cada bloque lleva su ícono: al clickearlo abre su "contrato" — contenido,
fecha sellada, hash, hash anterior, la fórmula y si ese bloque quedó dentro de la
parte rota de la cadena.

Probalo rompiéndola a mano:

```bash
make seed     # -> {"valid":true,"blocks":3,"brokenAt":null}
make break    # -> {"valid":false,"blocks":3,"brokenAt":1}
```

`make break` es un `UPDATE` crudo por psql, por fuera de la API: exactamente el
ataque que la cadena existe para delatar. Recargá el front y el escudo está rojo.
La cadena queda rota hasta que devuelvas el contenido original o corras
`make reset` — y los tests de integración fallan mientras tanto, que es
justamente lo que tiene que pasar.

No hay red, ni consenso, ni wallets: es un ledger a prueba de manipulación dentro
de tu propio Postgres. Las filas creadas antes de la migración `V2` no son bloques
y la verificación las ignora.

En producción el front vive en otro dominio (`tudominio.com`) y le pega a
`api.tudominio.com`: ahí sí hay CORS, y ese origen tiene que estar en
`CORS_ORIGINS`. En local no hay CORS: `ng serve` y la API comparten `localhost`
y ese origen ya viene permitido por default.

## El módulo de IA

Dos cosas distintas detrás del mismo proveedor:

**Chat** (`/ai/chat`) — streaming token a token por SSE, con historial guardado
en Postgres. El front pinta la respuesta mientras se genera, que es la diferencia
entre una demo que parece viva y una que parece colgada. El historial se recorta
a los últimos `AI_HISTORY_TURNS` turnos: más contexto es más tokens, más plata y
más latencia.

**RAG** (`/ai/documents` + `/ai/ask`) — el documento se parte en pedazos con
solape, cada pedazo se vectoriza y se guarda en pgvector, y al preguntar el back
busca los más parecidos por distancia coseno y se los pasa al modelo como
contexto. La respuesta viene con las fuentes y su similitud, así se ve de dónde
salió cada cosa en vez de confiar y rezar.

```bash
cp env.example .env       # y poné DEEPINFRA_API_KEY
make ai                   # {"configured":true,"chatModel":"…","documents":[]}
```

Sin la key el módulo responde 503 con el mensaje de qué falta, no un stacktrace.

El proveedor es **DeepInfra** por defecto, pero el adaptador habla el protocolo
de OpenAI: cambiando `DEEPINFRA_BASE_URL` sirve para OpenRouter, Together, vLLM
o un Ollama local. Cambiar de modelo es una variable de entorno
(`AI_CHAT_MODEL`). **Ojo con los embeddings**: `VECTOR(1024)` en `V4__ai.sql`
tiene que coincidir con la dimensión de `AI_EMBEDDING_MODEL`; si cambiás a un
modelo de otra dimensión, hace falta una migración nueva.

## El geovisor

Leaflet sobre OpenStreetMap (sin API key de nadie) y PostGIS del lado del back.

- `GET /geo/features?bbox=minLon,minLat,maxLon,maxLat` — el mapa pide solo lo que
  entra en pantalla. El `bbox` sale de `map.getBounds().toBBoxString()` de Leaflet
  y se filtra con `ST_Intersects` sobre el índice GIST, no en la app.
- `GET /geo/features/near?lon=&lat=&meters=` — distancia real en metros
  (`::geography`, no grados), ordenado de más cerca a más lejos.
- `POST /geo/features` — recibe GeoJSON y lo guarda con `ST_GeomFromGeoJSON`.

La columna es `geometry(Geometry, 4326)`, o sea la misma tabla guarda puntos,
líneas y polígonos. Los datos de ejemplo son cuatro features de Bogotá; se van
con `DELETE FROM geo_features WHERE category = 'demo'`.

En el front el mapa recarga la capa en cada `moveend`, y los puntos son
`circleMarker` en vez de `marker`: el ícono por defecto de Leaflet apunta a PNGs
por ruta relativa y se rompe al empaquetar.

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
docker compose -f docker-compose.prod.yml up -d --build db   # construye Postgres+PostGIS+pgvector
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

> `--build db` solo hace falta la primera vez (y cuando cambie
> `docker/postgres/Dockerfile`). Si el proyecto ya venía corriendo con
> `postgres:18-alpine`, la base se reinicializa: alpine usa musl y esta imagen
> glibc, así que **hacé `pg_dump` antes** si los datos te importan.

No hay paso de migraciones: **Flyway corre solo al arrancar la app**.

### 8. Verificar

```bash
curl -sI https://api.tudominio.com/health      # 200 — dale ~60s al certificado
curl -X POST https://api.tudominio.com/ledger -H 'Content-Type: application/json' -d '{"content":"ok"}'
curl https://api.tudominio.com/ledger/verify
```

Y abrí `https://api.tudominio.com/swagger-ui.html` en el browser.

De acá en más, cada push a `main` que toque `apps/api/**` despliega solo.

### 9. El front, en Cloudflare Pages

El front **no va al VPS**: es estático, lo sirve Cloudflare gratis desde su CDN.

1. Cloudflare → **Workers & Pages** → **Create** → **Pages** → conectá el repo.
2. **Root directory:** `apps/web`
3. **Build command:** `npm ci && npm run build`
4. **Output directory:** `dist/web/browser`
5. **Custom domain:** `tudominio.com` (y `www`), que quedan **proxied (naranja)**.

> Si venías del front estático de una versión anterior de la plantilla, tenés que
> actualizar los pasos 3 y 4 a mano en el proyecto de Pages: Angular sí necesita build.

El front deduce la URL del back del dominio: si está en `tudominio.com`, pega a
`https://api.tudominio.com`. Si tu setup no sigue esa convención, cambiá
`API_BASE` en `apps/web/src/app/core/api.ts`.

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

`apps/web/src/app/core/api.ts` arma la URL del back con `https://api.` + el hostname
sin `www.`. Mientras mantengas la convención `api.<dominio>`, se adapta solo. Si la
rompés, ahí sí editás `API_BASE`.

### Verificar

```bash
dig @1.1.1.1 +short api.nuevo.com nuevo.com
curl -sI https://api.nuevo.com/health                  # 200
curl -sI https://nuevo.com                             # 200
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Origin: https://nuevo.com' https://api.nuevo.com/ledger   # 200, no 403
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
