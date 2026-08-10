# ── Build ──────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build

# El pom va solo primero: mientras no cambien las dependencias, Docker reusa
# esta capa y no vuelve a bajar medio Maven Central en cada build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── Runtime ────────────────────────────────────────────────────────────────
# JRE, no JDK: la imagen final no necesita compilador.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Usuario sin privilegios: si alguien escapa del proceso, no es root.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080

# La JVM por defecto ignora el límite de memoria del contenedor y se cree dueña
# de toda la RAM del host -> OOM-kill. MaxRAMPercentage la ata al límite real.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
