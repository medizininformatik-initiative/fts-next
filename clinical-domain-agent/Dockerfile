FROM eclipse-temurin:21.0.11_10-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

# GNU wget for mTLS-aware healthchecks; BusyBox wget lacks --certificate,
# --private-key, and --ca-certificate.
RUN apk add --no-cache -q wget

COPY --chown=nobody:nobody target/clinical-domain-agent.jar /app/clinical-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./clinical-domain-agent.jar" ]
