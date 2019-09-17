#!/bin/sh

HZL_TYPE=${HAZELCAST_TYPE:-enterprise}

echo "** Using ${HZL_TYPE} type of Hazelcast **"

java $JAVA_OPTS -Dlogback.configurationFile=/logback.xml -Dhazelcast.client.config=/configs/hazelcast-client.xml -Dhazelcast.logging.type=slf4j -cp "/configs/custom-plugins/*:/libs/*:/libs/hazelcast/vertx-hazelcast-${HZL_TYPE}-impl.jar:app.jar" com.cloudentity.edge.Application run com.cloudentity.edge.Application -conf /configs/meta-config.json
