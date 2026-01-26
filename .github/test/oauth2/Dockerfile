FROM registry.access.redhat.com/ubi9@sha256:c8df11b65b18fcf384d5b7fa53e2a92f381a2d9ad78b9de41dc6f2aa161a0e04 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.2@sha256:fb31a59deb46f746f2aaa25adc5da39ceccac4fd22d36a519562b0bf02e8df20
COPY --from=ubi-micro-build /mnt/rootfs /
