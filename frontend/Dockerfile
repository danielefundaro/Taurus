FROM node:12.18.2 AS builder

COPY . ./application
WORKDIR /application

RUN npm i
RUN $(npm bin)/ng build --prod  --baseHref=/

FROM nginx:stable
COPY --from=builder /application/dist/frontend /usr/share/nginx/html

EXPOSE 443 80

CMD ["nginx", "-g", "daemon off;"]
