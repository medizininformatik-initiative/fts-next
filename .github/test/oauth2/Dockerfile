FROM registry.access.redhat.com/ubi9@sha256:22e95731596d661ff08daabaa5ef751b20ac42d0a58492dac5efa7373f471389 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.1@sha256:b80a48090594367bd8cf6fe2019466ac4ea49de4d0830fb2a43256eda37b18f5
COPY --from=ubi-micro-build /mnt/rootfs /
