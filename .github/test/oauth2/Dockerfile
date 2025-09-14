FROM registry.access.redhat.com/ubi9@sha256:8f1496d50a66e41433031bf5bdedd4635520e692ccd76ffcb649cf9d30d669af AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.3.4@sha256:2b32a51a31e8d780d9fa9a69a59ead69975263c61b5dd13559090e22aa26f100
COPY --from=ubi-micro-build /mnt/rootfs /
