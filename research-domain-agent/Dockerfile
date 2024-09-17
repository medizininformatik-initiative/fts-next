FROM eclipse-temurin:21.0.4_7-jre@sha256:6dd753778dec1bae7c1c2c948231e9d0b335b213dd85b575f6f0e72b326d7ed4

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
