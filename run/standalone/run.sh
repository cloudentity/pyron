#!/bin/sh

env $(grep -v '^#' env) java -cp edge.jar com.cloudentity.edge.Application run com.cloudentity.edge.Application -conf meta-config.json