# =========================
# BUILD STAGE
# =========================
FROM node:24.12.0-alpine AS builder

WORKDIR /app

# Copia dipendenze
COPY package*.json ./

# Installazione dipendenze
RUN npm ci

# Copia progetto
COPY . .

# Build Angular production
RUN npm run build -- --configuration production

# =========================
# RUNTIME STAGE
# =========================
FROM nginx:1.31.0-alpine3.23-slim

# Pulizia default nginx
RUN rm -rf /usr/share/nginx/html/*

# Copia build Angular
COPY --from=builder /app/dist/sakai-ng/browser /usr/share/nginx/html

# Configurazione nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
EXPOSE 443

CMD ["nginx", "-g", "daemon off;"]