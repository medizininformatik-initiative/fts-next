FROM eclipse-temurin:21.0.5_11-jre-alpine@sha256:b396250d5ae52dbc6ec6ceaf78dec50c03e9866731486598def705aaa58d5cba

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
