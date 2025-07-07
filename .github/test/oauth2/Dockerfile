FROM registry.access.redhat.com/ubi9@sha256:c73e2517941b384059eba8ea4b6ac68dad39a0a2cf0e65c753c778c87c87c321 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.0@sha256:08a31919cfcd814bf1b465142b1a716c4d1a8830f772bb5c9dffcbd96de3fba6
COPY --from=ubi-micro-build /mnt/rootfs /
