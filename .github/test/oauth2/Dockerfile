FROM registry.access.redhat.com/ubi9@sha256:e2ec76831504fcb2e3af8f243fba7a08398e343ee27296adcc8c80f82818b22d AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.2@sha256:922e443a962b8ad0dffa3c17061e8136583d7bc59209a1d1fc059816467c3f69
COPY --from=ubi-micro-build /mnt/rootfs /
