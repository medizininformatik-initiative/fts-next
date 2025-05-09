FROM registry.access.redhat.com/ubi9@sha256:f4ebd46d3ba96feb016d798009e1cc2404c3a4ebdac8b2479a2ac053e59f41b4 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.2.4@sha256:4a81762677f8911c6266e3dea57a2d78fa17bd63408debbf23afd8cc46fe256e
COPY --from=ubi-micro-build /mnt/rootfs /
