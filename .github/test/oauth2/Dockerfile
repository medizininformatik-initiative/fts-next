FROM registry.access.redhat.com/ubi9@sha256:b68c21b2dd3e72abcf2f8dcfc77580e4030564d1243bfcb7cd64ccc5aa3e0a25 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.3@sha256:6a7217a100bd3e5de4063a27a538ef999a3c5a88c4b4ec0ffc0a642aee7b2597
COPY --from=ubi-micro-build /mnt/rootfs /
