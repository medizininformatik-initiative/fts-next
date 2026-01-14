FROM registry.access.redhat.com/ubi9@sha256:2c9bb68a869abf7d7417f6639509ab5eb8500d8429ea11ab59e677be5545162b AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.5.1@sha256:b80a48090594367bd8cf6fe2019466ac4ea49de4d0830fb2a43256eda37b18f5
COPY --from=ubi-micro-build /mnt/rootfs /
