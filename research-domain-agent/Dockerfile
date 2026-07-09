FROM eclipse-temurin:25.0.3_9-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0

# Patch stale Alpine OS packages flagged by Trivy. Versions are pinned so
# Renovate tracks updates (see renovate.json5). p11-kit and p11-kit-trust
# share one version and must upgrade in lockstep.
# renovate: datasource=repology depName=alpine_3_23/libexpat versioning=loose
ARG LIBEXPAT_VERSION="2.8.2-r0"
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
