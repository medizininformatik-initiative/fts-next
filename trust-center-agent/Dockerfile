FROM eclipse-temurin:21.0.3_9-jre@sha256:11cb6264789abec7478d085b7bd3f7263dbea23ca261c795c03ade9d4d449ff4

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
