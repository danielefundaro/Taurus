#!/bin/bash

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d "$POSTGRES_DB"  <<-EOSQL
     create database taurus_keycloak;
EOSQL