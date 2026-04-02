FROM registry.access.redhat.com/ubi9@sha256:9e6e193bfc3596a84d2a32f42d6b1552398ec9735b9a4e893a0fc3c6fbccb381 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.7@sha256:45ae20191531eb608ddb0b775d012b40d3e4f942697f3214694887dd7c108d13
COPY --from=ubi-micro-build /mnt/rootfs /
