#!/usr/bin/env bash
# Controlla che il manifest di produzione sia valido e coerente, senza avviare niente.
#
# Riempie le variabili obbligatorie con valori finti, chiede a Compose di risolvere il manifest e
# verifica le scelte che non devono regredire: nessuna porta pubblicata fuori dal loopback, i dati
# su volumi, le restrizioni del servizio OMR, Keycloak in modalita' produzione.
#
#   scripts/ops/validate-deploy-manifest.sh
#
# Serve Docker Compose v2. Non legge il `.env` dell'installazione: i valori finti stanno qui.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
deploy="$root/deploy"
env_file="$(mktemp)"
trap 'rm -f "$env_file"' EXIT

# Valori finti, solo per la risoluzione del manifest: nessuno di questi raggiunge un servizio.
cat > "$env_file" <<'ENV'
TAURUS_VERSION=0.0.0-validazione
TAURUS_OMR_VERSION=0.0.0-validazione
TAURUS_PUBLIC_URL=https://taurus.esempio.it
TAURUS_DB_USERNAME=taurus
TAURUS_DB_PASSWORD=finta
TAURUS_KEYCLOAK_DB_PASSWORD=finta
TAURUS_KEYCLOAK_ADMIN_USERNAME=amministratore
TAURUS_KEYCLOAK_ADMIN_PASSWORD=finta
TAURUS_OIDC_CLIENT_SECRET=finto
TAURUS_RABBITMQ_USERNAME=taurus
TAURUS_RABBITMQ_PASSWORD=finta
TAURUS_VAPID_PUBLIC_KEY=chiave-pubblica-finta
TAURUS_VAPID_PRIVATE_KEY=chiave-privata-finta
TAURUS_VAPID_SUBJECT=mailto:tecnico@esempio.it
TAURUS_OMR_API_KEY=0123456789abcdef
ENV

resolved="$(docker compose --env-file "$env_file" -f "$deploy/compose.yml" config)"

problems=0
fail() {
    echo "  NO  $1"
    problems=$((problems + 1))
}
ok() {
    echo "  ok  $1"
}

echo "== Manifest risolto"
ok "compose.yml e' valido"

# Le porte pubblicate: solo quella del frontend e quella del sito informativo, e solo sul loopback.
published="$(printf '%s' "$resolved" | grep -cE '^\s+published:' || true)"
loopback="$(printf '%s' "$resolved" | grep -E '^\s+host_ip:' | tr -d '"' | grep -c '127\.0\.0\.1' || true)"
if [ "$published" -le 1 ]; then
    ok "una sola porta pubblicata nel profilo predefinito"
else
    fail "il profilo predefinito pubblica $published porte: solo il frontend deve esporre una porta"
fi
# Ogni porta pubblicata deve dichiarare l'indirizzo del loopback: una porta senza host_ip ascolta su
# tutte le interfacce della macchina, che e' il difetto da non far tornare.
if [ "$published" -eq "$loopback" ]; then
    ok "ogni porta pubblicata e' legata al loopback"
else
    fail "porte pubblicate: $published, legate al loopback: $loopback. Una porta senza 127.0.0.1 ascolta su tutte le interfacce"
fi

echo "== Dati e restrizioni"
for expectation in \
    'taurus-db:/var/lib/postgresql/data=il database sta su un volume' \
    'taurus-media:/var/lib/taurus/media=i media stanno su un volume' \
    'taurus-rabbit:/var/lib/rabbitmq=la coda sta su un volume'; do
    needle="${expectation%%=*}"
    what="${expectation#*=}"
    source="${needle%%:*}"
    target="${needle#*:}"
    if printf '%s' "$resolved" | grep -q "source: $source" && printf '%s' "$resolved" | grep -q "target: $target"; then
        ok "$what"
    else
        fail "$what: manca il volume $needle"
    fi
done

if printf '%s' "$resolved" | grep -q 'read_only: true'; then
    ok "il servizio OMR gira con il file system in sola lettura"
else
    fail "il servizio OMR non ha piu' il file system in sola lettura"
fi
if printf '%s' "$resolved" | grep -q 'no-new-privileges:true'; then
    ok "il servizio OMR non puo' acquisire privilegi nuovi"
else
    fail "il servizio OMR non ha piu' no-new-privileges"
fi

echo "== Keycloak"
if printf '%s' "$resolved" | grep -q 'start-dev'; then
    fail "Keycloak parte con start-dev: in produzione va avviato in modalita' produzione"
else
    ok "Keycloak non parte con start-dev"
fi
if printf '%s' "$resolved" | grep -q 'KC_PROXY_HEADERS: xforwarded'; then
    ok "Keycloak si fida delle intestazioni inoltrate dal proxy"
else
    fail "manca KC_PROXY_HEADERS: senza, le redirezioni dell'accesso tornano su indirizzi sbagliati"
fi

echo "== Certificati della CIE"
# Il bundle delle CA della CIE deve arrivare al backend, e in sola lettura: e' la base della fiducia
# di CieSign, non un file che il container possa riscrivere.
mount="$(printf '%s' "$resolved" | grep -A3 'target: /etc/taurus/cie' || true)"
if [ -n "$mount" ]; then
    ok "il bundle delle CA della CIE e' montato nel backend"
    if printf '%s' "$mount" | grep -q 'read_only: true'; then
        ok "il montaggio del bundle e' in sola lettura"
    else
        fail "il bundle delle CA della CIE e' montato in scrittura: va montato con :ro"
    fi
else
    fail "manca il montaggio del bundle delle CA della CIE: senza, CieSign non viene offerto"
fi
if printf '%s' "$resolved" | grep -q 'SIGNATURE_CIE_TRUST_ANCHORS: /etc/taurus/cie/'; then
    ok "il backend cerca i certificati della CIE dentro il montaggio"
else
    fail "SIGNATURE_CIE_TRUST_ANCHORS non punta dentro /etc/taurus/cie"
fi

echo "== Profilo Spring"
if printf '%s' "$resolved" | grep -q 'SPRING_PROFILES_ACTIVE: prod'; then
    ok "il backend gira con il profilo prod"
else
    fail "il backend non dichiara il profilo prod"
fi

echo
if [ "$problems" -gt 0 ]; then
    echo "Manifest di produzione non valido. Controlli falliti: $problems."
    exit 1
fi

echo "Manifest di produzione valido: porte, volumi, restrizioni del servizio OMR, Keycloak, certificati della CIE e profilo."
