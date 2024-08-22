FROM eclipse-temurin:21.0.4_7-jre@sha256:0d254ad44c7f182477e4e20c6d9175a6315cdb88185cc0c03f45e0948842e7c1

COPY --chown=nobody:nobody target/clinical-domain-agent.jar /app/clinical-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java",\
      "-Dcom.sun.management.jmxremote", \
      "-Dcom.sun.management.jmxremote.port=9090", \
      "-Dcom.sun.management.jmxremote.rmi.port=9090", \
      "-Dcom.sun.management.jmxremote.authenticate=false", \
      "-Dcom.sun.management.jmxremote.ssl=false", \
      "-jar", \
      "./clinical-domain-agent.jar" ]
