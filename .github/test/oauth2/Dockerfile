FROM registry.access.redhat.com/ubi9@sha256:7ff0b510498fa9f368a58e735e0c57f3497fd3205dbc2ea5e6e8ddf84f48752f AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.3@sha256:6a7217a100bd3e5de4063a27a538ef999a3c5a88c4b4ec0ffc0a642aee7b2597
COPY --from=ubi-micro-build /mnt/rootfs /
