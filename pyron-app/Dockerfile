FROM openjdk:8u312-jre
LABEL maintainer="Cloudentity <contact@cloudentity.com>"

ARG version

ENV SU_EXEC_VERSION=0.2-r0 \
	CURL_VERSION=7.64.0-r5

ENV GC_LOG_FILE_COUNT=1 \
	GC_LOG_FILE_SIZE=50M \
	JMX_PORT=9099 \
	JMX_HOST=0.0.0.0 \
	MEM_PERCENT=60.0 \ 
	CPU_COUNT=0

ENV GC_PRINT_OPTS="-XX:+PrintGCDetails -XX:+PrintGCDateStamps \
	-XX:+PrintGCTimeStamps -XX:+PrintTenuringDistribution \
	-Xloggc:/logs/gc.log -XX:+UseGCLogFileRotation \
	-XX:NumberOfGCLogFiles=$GC_LOG_FILE_COUNT \
	-XX:GCLogFileSize=$GC_LOG_FILE_SIZE" \
	GC_EXTENDED_PRINT_OPTS=-XX:+PrintGCApplicationStoppedTime \
	GC_OPTS=-XX:+UseParallelOldGC \
	ERROR_OPTS="-XX:+CrashOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError \
	-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/logs/ \
	-XX:ErrorFile=/logs/error.log" \
	MEM_OPTS="-XX:InitialRAMPercentage=$MEM_PERCENT \
	-XX:MaxRAMPercentage=$MEM_PERCENT -XX:MinRAMPercentage=$MEM_PERCENT" \
	NON_HEAP_MEM_OPTS="-XX:CompressedClassSpaceSize=12m \
	-XX:MaxMetaspaceSize=102m -XX:ReservedCodeCacheSize=64m" \
	VM_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
	-XX:+UseContainerSupport" \
	CPU_OPTS=-XX:ActiveProcessorCount=$CPU_COUNT \
	JMX_OPTS="-Dcom.sun.management.jmxremote.authenticate=false \
	-Dcom.sun.management.jmxremote.local.only=false \
	-Dcom.sun.management.jmxremote.ssl=false \
	-Dcom.sun.management.jmxremote=true" \
	JMX_CONFIG_OPTS="-Dcom.sun.management.jmxremote.port=$JMX_PORT \
	-Djava.rmi.server.hostname=$JMX_HOST \
	-Dcom.sun.management.jmxremote.rmi.port=$JMX_PORT" \
	NETWORK_OPTS=-Djava.net.preferIPv4Stack=true

RUN groupadd -r cloudentity && useradd -u 9999 \
	--no-log-init -r -g cloudentity cloudentity

RUN mkdir /logs /.vertx /file-uploads /plugin-jars && \
	chown -R cloudentity:cloudentity /logs /.vertx /file-uploads /plugin-jars

COPY --chown=cloudentity:cloudentity src/main/docker/docker-entrypoint.sh \
									 src/main/docker/healthcheck.sh \
									 src/main/docker/logback.xml /
COPY --chown=cloudentity:cloudentity target/pyron-app-${version}-fat.jar /app.jar
COPY --chown=cloudentity:cloudentity src/main/docker/meta-config.json \
									 src/main/docker/system.json \
									 src/main/docker/rules.json /configs/

USER cloudentity

HEALTHCHECK --interval=10s --timeout=3s CMD /healthcheck.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

EXPOSE 8080 9099

CMD ["pyron"]
