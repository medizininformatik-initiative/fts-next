FROM registry.access.redhat.com/ubi9@sha256:ac1eee4b85ab5feed38bf0f743b9278ceb1f9105e33260d37dbe53dc218128ad AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.6.1@sha256:26ae26445475f7fac5f90ee138b1bdb64324f5815fb16133ffdbdb122d97c4d8
COPY --from=ubi-micro-build /mnt/rootfs /
