FROM registry.access.redhat.com/ubi9@sha256:1f84f5cdae202944d9806dd9ce4800c889c5e5c8bbb29f28d42f7d28cbf709bd AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.2@sha256:fb31a59deb46f746f2aaa25adc5da39ceccac4fd22d36a519562b0bf02e8df20
COPY --from=ubi-micro-build /mnt/rootfs /
