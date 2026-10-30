FROM eclipse-temurin:25.0.5_8-jre-alpine@sha256:56f273059de6b13a6b49fe0ded691ed2d0399bbee76156a8f2d385f7524a3d43

# Patch stale Alpine OS packages flagged by Trivy. Versions are pinned so
# Renovate tracks updates (see renovate.json5). openssl, libssl3 and
# libcrypto3 share one version and must upgrade in lockstep.
# renovate: datasource=repology depName=alpine_3_24/libexpat versioning=loose
ARG LIBEXPAT_VERSION="2.9.0-r0"
# renovate: datasource=repology depName=alpine_3_24/openssl versioning=loose
ARG OPENSSL_VERSION="3.5.9-r0"

# GNU wget for mTLS-aware healthchecks; BusyBox wget lacks --certificate,
# --private-key, and --ca-certificate.
RUN apk add --no-cache -q \
      "libexpat=${LIBEXPAT_VERSION}" \
      "libcrypto3=${OPENSSL_VERSION}" \
      "libssl3=${OPENSSL_VERSION}" \
      "openssl=${OPENSSL_VERSION}" \
      wget

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
