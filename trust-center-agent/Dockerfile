FROM eclipse-temurin:21.0.4_7-jre@sha256:0d254ad44c7f182477e4e20c6d9175a6315cdb88185cc0c03f45e0948842e7c1

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
