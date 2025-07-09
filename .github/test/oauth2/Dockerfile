FROM registry.access.redhat.com/ubi9@sha256:e5ab898b4f3e91e31b4d202e92b4ca409ac18c2de77c4813807b3761332bf556 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.1@sha256:d9b4b8ff1627e43472cee278bab2da8dafeb082144addd2bfb0faa56a9be47d6
COPY --from=ubi-micro-build /mnt/rootfs /
