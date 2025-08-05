FROM registry.access.redhat.com/ubi9@sha256:836460d59c1fd64297328bae646cd7f56dcebc039fdbb8d14112597895f08d07 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.2@sha256:922e443a962b8ad0dffa3c17061e8136583d7bc59209a1d1fc059816467c3f69
COPY --from=ubi-micro-build /mnt/rootfs /
