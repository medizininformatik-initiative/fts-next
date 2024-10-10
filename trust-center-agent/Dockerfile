FROM eclipse-temurin:21.0.4_7-jre@sha256:e152704c7f25124a475dbac0cabfa4b8a5b165eb5da759d41b0afaf331cd830b

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
