#!/bin/sh
(
    set -eu
    export APP_PASSWORD="$(cat /run/secrets/marketplace_app_password)"
    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
\getenv app_password APP_PASSWORD
CREATE ROLE marketplace_app LOGIN PASSWORD :'app_password';
ALTER DATABASE marketplace OWNER TO marketplace_app;
SQL
)
