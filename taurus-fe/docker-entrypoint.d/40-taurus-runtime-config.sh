#!/bin/sh
# Scrive la configurazione che l'applicazione legge all'avvio (`src/runtime-config.ts`).
#
# L'immagine è costruita una volta e servita a browser che stanno fuori dalla rete del deploy: gli
# indirizzi dell'API e di Keycloak non possono essere fissati nel bundle. Il file viene riscritto a
# ogni avvio del container, anche quando nessuna variabile è impostata, così la copia di sviluppo
# che il bundle porta con sé non sopravvive mai in produzione.
#
# L'entrypoint dell'immagine nginx esegue da solo gli script di questa cartella, in ordine di nome,
# prima di avviare il server.
set -eu

target="/usr/share/nginx/html/assets/config.json"

api_base_url="${TAURUS_API_BASE_URL:-/api}"
keycloak_base_url="${TAURUS_KEYCLOAK_BASE_URL:-/auth}"
keycloak_realm="${TAURUS_KEYCLOAK_REALM:-taurus}"
keycloak_client_id="${TAURUS_KEYCLOAK_CLIENT_ID:-web_app}"
vapid_public_key="${TAURUS_VAPID_PUBLIC_KEY:-CHANGE_ME}"

mkdir -p "$(dirname "$target")"
cat > "$target" <<CONFIG
{
  "baseUrl": "${api_base_url}",
  "vapidPublicKey": "${vapid_public_key}",
  "keycloak": {
    "baseurl": "${keycloak_base_url}",
    "realm": "${keycloak_realm}",
    "clientId": "${keycloak_client_id}"
  }
}
CONFIG

echo "Configurazione scritta in ${target}: API ${api_base_url}, Keycloak ${keycloak_base_url} (realm ${keycloak_realm}, client ${keycloak_client_id})."

if [ "${vapid_public_key}" = "CHANGE_ME" ]; then
    echo "TAURUS_VAPID_PUBLIC_KEY non impostata: le notifiche push restano disattivate." >&2
fi
