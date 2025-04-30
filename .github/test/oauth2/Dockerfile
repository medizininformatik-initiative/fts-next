FROM registry.access.redhat.com/ubi9@sha256:f4ebd46d3ba96feb016d798009e1cc2404c3a4ebdac8b2479a2ac053e59f41b4 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.2.2@sha256:df40860c716d0c2f941967e8ac3ca5e74094c09988298b2eb0ae9335ed34f8f6
COPY --from=ubi-micro-build /mnt/rootfs /
