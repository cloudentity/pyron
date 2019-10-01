FROM docker.artifactory.syntegrity.com/jre:1.6

USER root

RUN mkdir /.vertx /file-uploads /plugin-jars && chown -R cloudentity:cloudentity /.vertx /file-uploads /plugin-jars

USER cloudentity

ARG JAR_FILE
COPY --chown=cloudentity:cloudentity ${JAR_FILE} /app.jar
COPY --chown=cloudentity:cloudentity src/main/docker/run.sh src/main/docker/logback.xml /
COPY --chown=cloudentity:cloudentity src/main/docker/meta-config.json src/main/docker/system.json src/main/docker/rules.json /configs/

RUN chmod +x /run.sh

CMD ["/run.sh"]
