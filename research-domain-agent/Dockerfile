FROM eclipse-temurin:21.0.4_7-jre@sha256:041821a35cc42087d556dd8275dbe1735ae25484365d0ecff358d795bd93a73d

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
