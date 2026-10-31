#!/bin/bash
# Crea il database di Keycloak e il suo utente, alla prima inizializzazione del volume.
#
# Keycloak ha un ruolo suo, con la sua password: l'applicazione e il server di identità non si
# leggono i dati a vicenda, e la compromissione di una credenziale non apre l'altro database.
#
# PostgreSQL esegue gli script di questa cartella una volta sola, quando il volume dei dati è vuoto:
# su un volume già inizializzato non vengono rieseguiti, quindi non c'è rischio di sovrascrivere
# nulla.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=role="$KEYCLOAK_DB_USERNAME" \
    --set=database="$KEYCLOAK_DB_NAME" \
    --set=password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
-- :'password' e :"role" lasciano a psql la citazione dei valori: una password con un apostrofo non
-- spezza l'istruzione.
create role :"role" with login password :'password';
create database :"database" owner :"role";
SQL

echo "Database ${KEYCLOAK_DB_NAME} creato per l'utente ${KEYCLOAK_DB_USERNAME}."
