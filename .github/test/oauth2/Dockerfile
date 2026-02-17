FROM registry.access.redhat.com/ubi9@sha256:cecb1cde7bda7c8165ae27841c2335667f8a3665a349c0d051329c61660a496c AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.3@sha256:5a236ae4dd8ece77490115bace15a11a4d15e9cbcf58a490b95a7da2cd71d32a
COPY --from=ubi-micro-build /mnt/rootfs /
