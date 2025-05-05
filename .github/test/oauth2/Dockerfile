FROM registry.access.redhat.com/ubi9@sha256:f4ebd46d3ba96feb016d798009e1cc2404c3a4ebdac8b2479a2ac053e59f41b4 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.2.3@sha256:60f7adee39486c70607ca9b61394bcf7b9607f620efcef64b99f6fc5ffd031e2
COPY --from=ubi-micro-build /mnt/rootfs /
