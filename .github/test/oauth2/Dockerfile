FROM registry.access.redhat.com/ubi9@sha256:dbc1e98d14a022542e45b5f22e0206d3f86b5bdf237b58ee7170c9ddd1b3a283 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.4.0@sha256:5f3fb534cde6bf006d79f5912473e5d2c828c707cdfc52e16972803aca9d43dd
COPY --from=ubi-micro-build /mnt/rootfs /
