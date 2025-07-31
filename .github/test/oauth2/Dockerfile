FROM registry.access.redhat.com/ubi9@sha256:0fa3e48369775f03b640a28cd5b55e6410569fa149f1827240619169463a7833 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.2@sha256:922e443a962b8ad0dffa3c17061e8136583d7bc59209a1d1fc059816467c3f69
COPY --from=ubi-micro-build /mnt/rootfs /
