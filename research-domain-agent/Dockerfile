FROM eclipse-temurin:25.0.4_7-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682

# Patch stale Alpine OS packages flagged by Trivy. Versions are pinned so
# Renovate tracks updates (see renovate.json5). p11-kit and p11-kit-trust
# share one version and must upgrade in lockstep.
# renovate: datasource=repology depName=alpine_3_23/libexpat versioning=loose
ARG LIBEXPAT_VERSION="2.8.3-r0"
# renovate: datasource=repology depName=alpine_3_23/p11-kit versioning=loose
ARG P11_KIT_VERSION="0.26.2-r0"

# GNU wget for mTLS-aware healthchecks; BusyBox wget lacks --certificate,
# --private-key, and --ca-certificate.
RUN apk add --no-cache -q \
      "libexpat=${LIBEXPAT_VERSION}" \
      "p11-kit=${P11_KIT_VERSION}" \
      "p11-kit-trust=${P11_KIT_VERSION}" \
      wget

COPY --chown=nobody:nobody target/research-domain-agent.jar /app/research-domain-agent.jar
COPY --chown=nobody:nobody application.yaml                 /app/application.yaml

EXPOSE 8080

WORKDIR /app

VOLUME /app/projects

USER nobody
CMD [ "java", "-jar", "./research-domain-agent.jar" ]
