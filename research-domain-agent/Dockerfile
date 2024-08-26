FROM eclipse-temurin:21.0.4_7-jre@sha256:31e6ae243df22d948c940096b416e9b0b8b4424be38597fc869ac15193b37556

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
