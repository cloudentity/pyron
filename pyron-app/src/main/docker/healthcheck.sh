#!/bin/sh
set -e

HTTP_SERVER_PORT=${HTTP_SERVER_PORT:-8080}
export HTTP_SERVER_PORT=${PORT:-$HTTP_SERVER_PORT}

curl -k -f ${HEALTHCHECK_URL:-http://localhost:$HTTP_SERVER_PORT/alive} || exit 1
