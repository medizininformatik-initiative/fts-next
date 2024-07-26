FROM eclipse-temurin:21.0.4_7-jre@sha256:33c033fbc177c46ae097fc01575e6a0df665dbecc49d1cff2227b969672697cb

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
