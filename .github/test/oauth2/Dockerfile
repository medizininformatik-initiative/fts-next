FROM registry.access.redhat.com/ubi9@sha256:b88227436597e960f70db8eadcaf18ffa5b6d1a615888f709dcae3fba1540c61 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.1.3@sha256:2ce6c7c70994c70dbbd70b372a5422c3b4eebb32583175eac03751320609e52c
COPY --from=ubi-micro-build /mnt/rootfs /
