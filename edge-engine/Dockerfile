FROM docker.artifactory.syntegrity.com/jre:1.6

USER root

RUN mkdir /.vertx /verticles /file-uploads && chown -R cloudentity:cloudentity /.vertx /verticles /file-uploads 

USER cloudentity

ARG HAZELCAST_VERSION
COPY --chown=cloudentity:cloudentity target/deps/vertx-hazelcast-enterprise-impl-${HAZELCAST_VERSION}.jar /libs/hazelcast/vertx-hazelcast-enterprise-impl.jar
COPY --chown=cloudentity:cloudentity target/deps/vertx-hazelcast-impl-${HAZELCAST_VERSION}.jar /libs/hazelcast/vertx-hazelcast-regular-impl.jar
COPY --chown=cloudentity:cloudentity target/deps/bcpkix-jdk15on-1.52.jar target/deps/bcprov-jdk15on-1.62.jar /libs/

ARG JAR_FILE
COPY --chown=cloudentity:cloudentity ${JAR_FILE} /app.jar
COPY --chown=cloudentity:cloudentity src/main/docker/run.sh src/main/docker/logback.xml /
COPY --chown=cloudentity:cloudentity src/main/docker/meta-config.json src/main/docker/secrets.json src/main/docker/rules.json /configs/

RUN chmod +x /run.sh

CMD ["/run.sh"]
