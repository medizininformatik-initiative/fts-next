FROM registry.access.redhat.com/ubi9@sha256:cecb1cde7bda7c8165ae27841c2335667f8a3665a349c0d051329c61660a496c AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.4@sha256:ae8efb0d218d8921334b03a2dbee7069a0b868240691c50a3ffc9f42fabba8b4
COPY --from=ubi-micro-build /mnt/rootfs /
