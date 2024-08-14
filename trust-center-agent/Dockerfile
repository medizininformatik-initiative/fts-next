FROM eclipse-temurin:21.0.4_7-jre@sha256:6a11a74b38efd5e291ed566eef2ccc07204f8828d8428d81d5d2a834ff296c00

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
