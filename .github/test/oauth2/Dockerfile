FROM registry.access.redhat.com/ubi9@sha256:7daaafccb9cb594b5a737d248ab6adcc783ea7055291d4fcd799a1201326ca16 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.4.6@sha256:d0d4037f17521a7f06137afd5a0eecb1f977f4ade773ae7755f1ee82cad8a576
COPY --from=ubi-micro-build /mnt/rootfs /
