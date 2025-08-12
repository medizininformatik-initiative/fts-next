FROM registry.access.redhat.com/ubi9@sha256:8851294389a8641bd6efcd60f615c69e54fb0e2216ec8259448b35e3d9a11b06 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.2@sha256:98fab020a3a490aba0978f237e2a06cd0ea42bf149c6cf10f11c0aaf27728ff2
COPY --from=ubi-micro-build /mnt/rootfs /
