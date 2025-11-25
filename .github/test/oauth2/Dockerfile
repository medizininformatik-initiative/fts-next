FROM registry.access.redhat.com/ubi9@sha256:dcd8128d7620b06e4bce291f30db6ffcfa339b04e6d66081e36eb8e2b1b700f8 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.4.6@sha256:d0d4037f17521a7f06137afd5a0eecb1f977f4ade773ae7755f1ee82cad8a576
COPY --from=ubi-micro-build /mnt/rootfs /
