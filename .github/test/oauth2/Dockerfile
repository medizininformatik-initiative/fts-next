FROM registry.access.redhat.com/ubi9@sha256:2e4eebec441e8bbc3459fcc83ddee0f7d3cfd219097b4110a37d7ff4fe0ff2e9 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.2.5@sha256:4883630ef9db14031cde3e60700c9a9a8eaf1b5c24db1589d6a2d43de38ba2a9
COPY --from=ubi-micro-build /mnt/rootfs /
