FROM eclipse-temurin:21.0.4_7-jdk

COPY --chown=nobody:nobody \
  target/clinical-domain-agent.jar \
  target/reactor-tools.jar \
  application.yaml \
  /app/

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-javaagent:./reactor-tools.jar", "-jar", "./clinical-domain-agent.jar" ]
