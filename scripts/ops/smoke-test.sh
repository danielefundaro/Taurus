#!/usr/bin/env bash
# Smoke test di un'installazione di Taurus: non scrive niente, non autentica nessuno.
#
# È il controllo del punto 8.4 del runbook operativo, eseguibile subito dopo un deploy e ripetibile
# a freddo: interroga l'origine pubblica come farebbe un browser e verifica che i pezzi ci siano e
# rispondano come devono. Ogni richiesta è una lettura: nessun dato dei tenant viene toccato, e
# nessun endpoint protetto viene chiamato con credenziali.
#
#   scripts/ops/smoke-test.sh https://taurus.esempio.it
#
# Con l'indirizzo del backend raggiungibile dalla rete interna, controlla anche la sua salute:
#
#   TAURUS_MANAGEMENT_URL=http://127.0.0.1:8080 scripts/ops/smoke-test.sh https://taurus.esempio.it
#
# Esce 0 se tutto risponde, 1 elencando quello che non torna.
set -uo pipefail

BASE_URL="${1:-${TAURUS_PUBLIC_URL:-}}"
REALM="${TAURUS_REALM:-taurus}"
MANAGEMENT_URL="${TAURUS_MANAGEMENT_URL:-}"

if [ -z "$BASE_URL" ]; then
    echo "Uso: scripts/ops/smoke-test.sh https://taurus.esempio.it" >&2
    exit 2
fi

BASE_URL="${BASE_URL%/}"
problems=0

fail() {
    echo "  NO  $1"
    problems=$((problems + 1))
}

ok() {
    echo "  ok  $1"
}

# Lo stato HTTP di una richiesta di sola lettura. `--max-time` tiene corto il fallimento di un
# servizio fermo, invece di lasciare lo script appeso.
status_of() {
    curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$1"
}

body_of() {
    curl -s --max-time 15 "$1"
}

headers_of() {
    curl -s -o /dev/null -D - --max-time 15 "$1"
}

expect_status() {
    local url="$1" expected="$2" what="$3" actual
    actual=$(status_of "$url")
    if [ "$actual" = "$expected" ]; then
        ok "$what ($expected)"
    else
        fail "$what: $actual invece di $expected"
    fi
}

echo "== Applicazione su $BASE_URL"
expect_status "$BASE_URL/" 200 "la pagina dell'applicazione risponde"
expect_status "$BASE_URL/health" 200 "il punto di salute del frontend risponde"

# Il router dell'applicazione vive nel browser: un indirizzo interno deve tornare l'index, non 404,
# altrimenti un collegamento condiviso o un ricaricamento della pagina si rompe.
expect_status "$BASE_URL/calendar" 200 "un indirizzo interno torna l'applicazione"

# La configurazione a runtime: è il file che dice al browser dove sono API e Keycloak. Se contiene
# ancora gli indirizzi di sviluppo, il deploy non ha passato le sue variabili.
config=$(body_of "$BASE_URL/assets/config.json")
if printf '%s' "$config" | grep -q '"baseUrl"'; then
    ok "la configurazione a runtime è servita"
    if printf '%s' "$config" | grep -q 'localhost'; then
        fail "la configurazione a runtime contiene ancora localhost: le variabili del container non sono impostate"
    else
        ok "la configurazione a runtime non contiene indirizzi di sviluppo"
    fi
else
    fail "la configurazione a runtime non è servita o non è leggibile"
fi

echo "== Intestazioni di sicurezza"
headers=$(headers_of "$BASE_URL/")
for header in x-content-type-options x-frame-options referrer-policy content-security-policy; do
    if printf '%s' "$headers" | tr 'A-Z' 'a-z' | grep -q "^$header:"; then
        ok "$header"
    else
        fail "manca l'intestazione $header"
    fi
done
if printf '%s' "$headers" | tr 'A-Z' 'a-z' | grep -q '^strict-transport-security:'; then
    ok "strict-transport-security"
else
    # La dichiara il reverse proxy dell'host, che è l'unico a sapere se la connessione era in TLS.
    fail "manca strict-transport-security: va dichiarata dal reverse proxy dell'host"
fi
if printf '%s' "$headers" | tr 'A-Z' 'a-z' | grep -qE '^server:.*[0-9]'; then
    fail "l'intestazione Server rivela la versione del server"
else
    ok "l'intestazione Server non rivela la versione"
fi

echo "== Identità su $BASE_URL/auth"
realm_config=$(body_of "$BASE_URL/auth/realms/$REALM/.well-known/openid-configuration")
if printf '%s' "$realm_config" | grep -q '"authorization_endpoint"'; then
    ok "il realm $REALM pubblica i suoi metadati"
    # L'issuer è quello che finisce nei token: se resta un indirizzo interno, il browser non riesce
    # a completare l'accesso e il backend rifiuta i token.
    if printf '%s' "$realm_config" | grep -q "\"issuer\":\"$BASE_URL/auth/realms/$REALM\"" ||
        printf '%s' "$realm_config" | grep -q "\"issuer\": *\"$BASE_URL/auth/realms/$REALM\""; then
        ok "l'issuer del realm è l'indirizzo pubblico"
    else
        fail "l'issuer del realm non è $BASE_URL/auth/realms/$REALM: controllare KC_HOSTNAME e le intestazioni inoltrate"
    fi
    if printf '%s' "$realm_config" | grep -q 'taurus-theme' || printf '%s' "$realm_config" | grep -q '"jwks_uri"'; then
        ok "le chiavi del realm sono pubblicate"
    fi
else
    fail "il realm $REALM non risponde su $BASE_URL/auth"
fi

echo "== API su $BASE_URL/api"
# L'unico endpoint pubblico dell'API: la sua risposta prova che l'inoltro al backend funziona.
expect_status "$BASE_URL/api/auth-info" 200 "l'API pubblica risponde attraverso l'inoltro"

# Senza token il resto dell'API deve rifiutare: un 200 qui vorrebbe dire aperto a chiunque.
anonymous=$(status_of "$BASE_URL/api/tracks")
case "$anonymous" in
    401 | 403) ok "l'API rifiuta una richiesta senza token ($anonymous)" ;;
    000) fail "l'API non risponde: nessuna connessione" ;;
    *) fail "l'API risponde $anonymous a una richiesta senza token, invece di 401 o 403" ;;
esac

# Gli endpoint di gestione non devono essere raggiungibili dall'origine pubblica.
for path in /management/health /management/env /management/prometheus; do
    public=$(status_of "$BASE_URL$path")
    case "$public" in
        404 | 403) ok "$path non è esposto pubblicamente ($public)" ;;
        *) fail "$path risponde $public dall'origine pubblica: va tenuto sulla rete interna" ;;
    esac
done

if [ -n "$MANAGEMENT_URL" ]; then
    echo "== Backend su $MANAGEMENT_URL"
    MANAGEMENT_URL="${MANAGEMENT_URL%/}"
    health=$(body_of "$MANAGEMENT_URL/management/health")
    if printf '%s' "$health" | grep -q '"status":"UP"'; then
        ok "la salute del backend è UP"
    else
        fail "la salute del backend non è UP: $health"
    fi
    expect_status "$MANAGEMENT_URL/management/health/readiness" 200 "la probe di readiness risponde"
    expect_status "$MANAGEMENT_URL/management/health/liveness" 200 "la probe di liveness risponde"
    for path in /management/env /management/configprops /management/threaddump /management/loggers; do
        restricted=$(status_of "$MANAGEMENT_URL$path")
        case "$restricted" in
            404 | 401 | 403) ok "$path non è esposto ($restricted)" ;;
            *) fail "$path risponde $restricted: in produzione non deve essere esposto" ;;
        esac
    done
fi

echo
if [ "$problems" -gt 0 ]; then
    echo "Smoke test non superato. Controlli falliti: $problems."
    exit 1
fi

echo "Smoke test superato: applicazione, configurazione, intestazioni, identità e API rispondono come devono."
