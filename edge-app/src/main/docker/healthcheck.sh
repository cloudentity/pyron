#!/bin/sh
set -e

PORT=${PORT:-8080}
curl -k -f ${HEALTHCHECK_URL:-http://localhost:$PORT/alive} || exit 1