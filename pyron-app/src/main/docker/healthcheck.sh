#!/bin/sh
set -e

curl -k -f ${HEALTHCHECK_URL:-http://localhost:$HTTP_SERVER_PORT/alive} || exit 1
