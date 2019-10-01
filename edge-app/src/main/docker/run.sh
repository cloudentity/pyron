#!/bin/sh

java $JAVA_OPTS -Dlogback.configurationFile=/logback.xml -cp "app.jar:plugin-jars/*" com.cloudentity.edge.Application run com.cloudentity.edge.Application -conf /configs/meta-config.json