FROM registry.access.redhat.com/ubi9@sha256:9e6e193bfc3596a84d2a32f42d6b1552398ec9735b9a4e893a0fc3c6fbccb381 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.6@sha256:8d44614c74798322c4e07fbe0ecb15cfbb5879d69b484628555f58ade06f0d8c
COPY --from=ubi-micro-build /mnt/rootfs /
