FROM eclipse-temurin:21.0.4_7-jre@sha256:7712b33154de37cab46c9db494c04d2ada77ed3fc0a672a822d0c805f35e1b99

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
