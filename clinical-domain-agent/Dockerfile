FROM eclipse-temurin:21.0.4_7-jre@sha256:e63addd757814eed22b40786e941770b6f8333e38273f88402ab71281eb8d38b

COPY --chown=nobody:nobody target/clinical-domain-agent.jar /app/clinical-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./clinical-domain-agent.jar" ]
