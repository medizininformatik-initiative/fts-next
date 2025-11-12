FROM registry.access.redhat.com/ubi9@sha256:bbac76ac0310eefd6298ed3ab3c1710e1c842f1f778800de0f49e660b402bdc5 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.4.4@sha256:c6459d5fae1b759f5d667ebdc6237ab3121379c3494e213898569014ede1846d
COPY --from=ubi-micro-build /mnt/rootfs /
