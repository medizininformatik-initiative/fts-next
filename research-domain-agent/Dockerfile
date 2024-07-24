FROM eclipse-temurin:21.0.3_9-jre@sha256:76307c251d4aa3fdcc1d964ea81621f6660ea8f82f5a578f901918346a8ff106

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
