FROM registry.access.redhat.com/ubi9@sha256:be214e191bbe3f4d0f16a5a4f5642e1f32fccd6fae7d2b6c6acb066ba51fb507 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.1.1@sha256:75ca4b2e4e954ff89c20ba8e5aeeef3bd0d250847fedb1c9752949823b319dda
COPY --from=ubi-micro-build /mnt/rootfs /
