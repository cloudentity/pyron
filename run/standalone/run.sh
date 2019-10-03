#!/bin/sh

env $(grep -v '^#' envs) java -cp "pyron.jar:plugin-jars/*" com.cloudentity.pyron.Application run com.cloudentity.pyron.Application -conf meta-config.json