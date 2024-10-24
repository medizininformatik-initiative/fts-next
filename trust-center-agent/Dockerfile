FROM eclipse-temurin:21.0.5_11-jre-alpine@sha256:03c1fb6fff9b28aa3be69f59df0a274c2c3984189726645cb383217229b25082

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
