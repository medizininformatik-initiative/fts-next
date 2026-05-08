FROM eclipse-temurin:21.0.11_10-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b

# GNU wget for mTLS-aware healthchecks; BusyBox wget lacks --certificate,
# --private-key, and --ca-certificate.
RUN apk add --no-cache -q wget

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
