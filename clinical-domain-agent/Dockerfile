FROM eclipse-temurin:21.0.4_7-jre@sha256:544831a4baf1e3284e8df9b66aef6be14e61984fcc6722bece1723c6769173b7

COPY --chown=nobody:nobody target/clinical-domain-agent.jar /app/clinical-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./clinical-domain-agent.jar" ]
