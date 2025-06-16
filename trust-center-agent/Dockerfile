FROM eclipse-temurin:21.0.7_6-jre-alpine@sha256:8728e354e012e18310faa7f364d00185277dec741f4f6d593af6c61fc0eb15fd

COPY --chown=nobody:nobody target/trust-center-agent.jar /app/trust-center-agent.jar
COPY --chown=nobody:nobody application.yaml              /app/application.yaml

EXPOSE 8080

WORKDIR /app

ENV JAVA_DEFAULT_DISABLED_ALGORITHMS="SSLv3, TLSv1, TLSv1.1, DTLSv1.0, RC4, DES, MD5withRSA, DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL, ECDH, MD5, DESede, RSA keySize < 2048, RSA_WITH_AES_128_CBC_SHA, DHE_RSA_WITH_AES_128_CBC_SHA, RSA_WITH_AES_256_CBC_SHA, DHE_RSA_WITH_AES_256_CBC_SHA, RSA_WITH_AES_128_CBC_SHA256, RSA_WITH_AES_256_CBC_SHA256, DHE_RSA_WITH_AES_128_CBC_SHA256, TLS_RSA_WITH_AES_128_CBC_SHA, TLS_RSA_WITH_AES_128_CBC_SHA256, TLS_RSA_WITH_AES_128_GCM_SHA256, TLS_RSA_WITH_AES_256_CBC_SHA, TLS_RSA_WITH_AES_256_CBC_SHA256, TLS_RSA_WITH_AES_256_GCM_SHA384"
ENV JAVA_TOOL_OPTIONS='\
  -Djdk.tls.disabledAlgorithms="${JAVA_DEFAULT_DISABLED_ALGORITHMS}" \
  -Djdk.certpath.disabledAlgorithms="${JAVA_DEFAULT_DISABLED_ALGORITHMS}" \
  -Djdk.tls.ephemeralDHKeySize=2048 \
  -Djdk.tls.rejectClientInitiatedRenegotiation=true'

USER nobody
CMD [ "java", "-jar", "./trust-center-agent.jar" ]
