FROM eclipse-temurin:21.0.4_7-jre@sha256:7712b33154de37cab46c9db494c04d2ada77ed3fc0a672a822d0c805f35e1b99

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
