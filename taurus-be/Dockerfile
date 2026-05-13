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

# Build jar
RUN mvn clean package -DskipTests

# =========================
# RUNTIME STAGE
# =========================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl per healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copia il jar generato
COPY --from=builder /build/target/*.jar app.jar

# Porta standard Spring Boot / WebFlux
EXPOSE 8080

# Variabili JVM configurabili
ENV JAVA_OPTS="\
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+UseG1GC"

# Startup
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
