FROM eclipse-temurin:25-jre-alpine@sha256:0be0f9b8c4c9c55bd97194ed84f0751f639f5712d9e99a45a0d65bf49f57bd08

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
