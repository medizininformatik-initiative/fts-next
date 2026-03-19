FROM registry.access.redhat.com/ubi9@sha256:05fa0100593c08b5e9dde684cd3eaa94b4d5d7b3cc09944f1f73924e49fde036 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.5@sha256:a7b0cb7a43a1235a61872883414d3f1d9a3ceac9df6e5907bd12202778a6265c
COPY --from=ubi-micro-build /mnt/rootfs /
