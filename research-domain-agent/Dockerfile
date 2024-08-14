FROM eclipse-temurin:21.0.4_7-jdk

COPY --chown=nobody:nobody \
  target/research-domain-agent.jar \
  target/reactor-tools.jar \
  application.yaml \
  /app/

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-javaagent:./reactor-tools.jar", "-jar", "./research-domain-agent.jar" ]
