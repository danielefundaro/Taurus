# =========================
# BUILD STAGE
# =========================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dipendenze Maven
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia sorgenti
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests

# =========================
# RUNTIME STAGE
# =========================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia il jar generato
COPY --from=builder /build/target/*.jar app.jar

# Porta standard Spring Boot / WebFlux
EXPOSE 8080

# Variabili JVM configurabili
ENV JAVA_OPTS="\
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+UseG1GC"

# Healthcheck opzionale
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Startup
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
