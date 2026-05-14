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
FROM nginx:1.31.0-alpine3.23-slim

# Pulizia contenuto default
RUN rm -rf /usr/share/nginx/html/*

# Copia build Angular
COPY --from=builder /app/dist/sakai-ng/browser /usr/share/nginx/html

# Config nginx opzionale
# COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
EXPOSE 443

CMD ["nginx", "-g", "daemon off;"]