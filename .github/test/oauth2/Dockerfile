FROM registry.access.redhat.com/ubi9@sha256:d342aa80781bf41c4c73485c41d8f1e2dbc40ee491633d9cafe787c361dd44ff AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.1.2@sha256:4704c51a25096e24379eafc81db5d7654a33b05e260a7eed2f4486b2daf00c45
COPY --from=ubi-micro-build /mnt/rootfs /
