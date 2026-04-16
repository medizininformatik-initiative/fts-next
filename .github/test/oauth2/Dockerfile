FROM registry.access.redhat.com/ubi9@sha256:8805abe5b8a32c826d46926c069f20e6a7f854d59d5bd75c55e68278aea65ccc AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.6.0@sha256:b0e5dbced1775de4d629f103c0a9cfc057decc62ce8d3cb1c54f8849a6c6eb62
COPY --from=ubi-micro-build /mnt/rootfs /
