# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS builder

WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

ARG SITE_URL=https://www.taurus.it
ARG PUBLIC_APP_URL=https://app.taurus.it
ARG PUBLIC_CONTACT_EMAIL=admin@taurus.it
ENV SITE_URL=$SITE_URL \
    PUBLIC_APP_URL=$PUBLIC_APP_URL \
    PUBLIC_CONTACT_EMAIL=$PUBLIC_CONTACT_EMAIL \
    ASTRO_TELEMETRY_DISABLED=1

RUN pnpm build

FROM nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
