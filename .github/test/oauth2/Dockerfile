FROM registry.access.redhat.com/ubi9@sha256:61bf5be4fcf7fa4d1ce6c8c0376dcb8b06e68aae6a0a54c4cf39c06116684962 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.2@sha256:922e443a962b8ad0dffa3c17061e8136583d7bc59209a1d1fc059816467c3f69
COPY --from=ubi-micro-build /mnt/rootfs /
