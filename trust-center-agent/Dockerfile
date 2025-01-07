FROM eclipse-temurin:21.0.5_11-jre-alpine@sha256:41502ff7105c996a588c68647da859759b5fc457bca6f4bf3edbc59e3b75423c

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
