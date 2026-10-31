# syntax=docker/dockerfile:1.7
# =========================
# BUILD STAGE
# =========================
FROM node:22-alpine AS builder

WORKDIR /app

# Copia package files
COPY package*.json ./

# Install dipendenze
RUN npm ci

# Copia sorgenti
COPY . .

# Build Angular production
RUN npm run build -- --configuration production

# =========================
# RUNTIME STAGE
# =========================
FROM nginx:1.29-alpine

# Pulizia contenuto default
RUN rm -rf /usr/share/nginx/html/*

# Il modello della configurazione: l'entrypoint dell'immagine sostituisce le variabili TAURUS_* e
# scrive /etc/nginx/conf.d/default.conf.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# Fuori da conf.d: i file .conf di quella cartella sono caricati come configurazioni a sé.
COPY security-headers.conf /etc/nginx/taurus-security-headers.conf
# Scrive assets/config.json dalle variabili d'ambiente, prima che nginx parta.
COPY --chmod=0755 docker-entrypoint.d/40-taurus-runtime-config.sh /docker-entrypoint.d/40-taurus-runtime-config.sh

# Copia build Angular
COPY --from=builder /app/dist/sakai-ng/browser /usr/share/nginx/html

# Indirizzi interni dei servizi a cui questo container inoltra /api e /auth, e valori che finiscono
# nella configurazione servita al browser. Sono i valori predefiniti del compose: una distribuzione
# li sovrascrive con le proprie variabili d'ambiente.
ENV NGINX_ENVSUBST_FILTER=^TAURUS_ \
    TAURUS_API_UPSTREAM=http://backend:8080 \
    TAURUS_KEYCLOAK_UPSTREAM=http://keycloak:9080 \
    TAURUS_DNS_RESOLVER=127.0.0.11 \
    TAURUS_MAX_UPLOAD_SIZE=500m \
    TAURUS_API_BASE_URL=/api \
    TAURUS_KEYCLOAK_BASE_URL=/auth \
    TAURUS_KEYCLOAK_REALM=taurus \
    TAURUS_KEYCLOAK_CLIENT_ID=web_app

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
