FROM eclipse-temurin:21.0.4_7-jre@sha256:544831a4baf1e3284e8df9b66aef6be14e61984fcc6722bece1723c6769173b7

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
