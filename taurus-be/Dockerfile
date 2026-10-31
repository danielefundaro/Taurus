# =========================
# BUILD STAGE
# =========================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dipendenze Maven
COPY pom.xml .
COPY sonar-project.properties .

# Cache dipendenze Maven
RUN mvn dependency:go-offline

# Copia sorgenti
COPY src ./src

# Build jar con il profilo di produzione: è quel profilo a fissare `spring.profiles.active=prod`
# dentro il jar. Senza `-Pprod` l'immagine partirebbe con il profilo di sviluppo, che punta a un
# PostgreSQL su localhost e porta con sé le credenziali di esempio.
RUN mvn -Pprod clean package -DskipTests

# =========================
# RUNTIME STAGE
# =========================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl per healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copia il jar generato
COPY --from=builder /build/target/*.jar app.jar

# Un utente senza privilegi e la radice dello storage dei media, che gli appartiene: un volume
# montato lì dentro eredita questo proprietario, quindi il processo può scrivere senza essere root.
RUN groupadd --system --gid 10001 taurus \
    && useradd --system --uid 10001 --gid taurus --home /app taurus \
    && mkdir -p /var/lib/taurus/media \
    && chown -R taurus:taurus /app /var/lib/taurus
USER taurus

# Porta standard Spring Boot / WebFlux
EXPOSE 8080

# Variabili JVM configurabili
ENV JAVA_OPTS="\
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+UseG1GC"

# Il profilo è già fissato nel jar: resta qui perché sia leggibile dall'esterno e sovrascrivibile.
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/management/health/readiness || exit 1

# Startup
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
