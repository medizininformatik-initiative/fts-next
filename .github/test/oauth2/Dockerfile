FROM registry.access.redhat.com/ubi9@sha256:1b99266db480e8aa6dfda6900697a07ec99334095b4f87d1687edfc06c965132 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.6.3@sha256:5fdbf2dbb5897cc34e82de49d13e23db011f9925089dbc555fc095f2c8bc1dac
COPY --from=ubi-micro-build /mnt/rootfs /
