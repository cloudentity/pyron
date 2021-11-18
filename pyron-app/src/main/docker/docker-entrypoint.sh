#!/bin/sh
set -e

HTTP_SERVER_PORT=${HTTP_SERVER_PORT:-8080}
export HTTP_SERVER_PORT=${PORT:-$HTTP_SERVER_PORT}

export JAVA_OPTS="${VM_OPTS} ${MEM_OPTS} ${NON_HEAP_MEM_OPTS} ${CPU_OPTS} ${GC_OPTS} ${GC_PRINT_OPTS} ${GC_EXTENDED_PRINT_OPTS} ${ERROR_OPTS} ${JMX_OPTS} ${JMX_CONFIG_OPTS} ${NETWORK_OPTS} ${VERTX_JAVA_OPTS} ${ADDITIONAL_JAVA_OPTS}"
export JAVA_OPTS=$(eval echo "${JAVA_OPTS}")

if [[ "$1" == "pyron" ]]; then

  if [ "$(id -u)" != "0" ]; then
    exec java $JAVA_OPTS \
      -Dlogback.configurationFile=/logback.xml \
      -cp "app.jar:plugin-jars/*" com.cloudentity.pyron.Application \
      run com.cloudentity.pyron.Application \
      -conf /configs/meta-config.json
  else
    exec su-exec cloudentity java $JAVA_OPTS \
      -Dlogback.configurationFile=/logback.xml \
      -cp "app.jar:plugin-jars/*" com.cloudentity.pyron.Application \
      run com.cloudentity.pyron.Application \
      -conf /configs/meta-config.json
  fi
fi

exec "$@"
