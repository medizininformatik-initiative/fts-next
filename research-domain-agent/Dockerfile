FROM eclipse-temurin:21.0.5_11-jre-alpine@sha256:decee204b9a1eb333c364ba4d859a6b1380eb13f0980d2acfd65c09fee53a48a

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
