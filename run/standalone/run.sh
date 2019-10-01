#!/bin/sh

env $(grep -v '^#' envs) java -cp "edge.jar:plugin-jars/*" com.cloudentity.edge.Application run com.cloudentity.edge.Application -conf meta-config.json