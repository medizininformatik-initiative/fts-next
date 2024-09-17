FROM eclipse-temurin:21.0.4_7-jre@sha256:6dd753778dec1bae7c1c2c948231e9d0b335b213dd85b575f6f0e72b326d7ed4

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
