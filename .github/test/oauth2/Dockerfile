FROM registry.access.redhat.com/ubi9@sha256:7fc5d8fcc2e047093bd053c070e838eed557cbc732d2fe6926267e441a008470 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.6.2@sha256:5afd40414096432903a5374ba4ba70b931dcca598a6bd9f44239fe66ef8d9737
COPY --from=ubi-micro-build /mnt/rootfs /
