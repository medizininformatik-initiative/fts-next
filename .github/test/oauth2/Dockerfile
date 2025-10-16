FROM registry.access.redhat.com/ubi9@sha256:dec374e05cc13ebbc0975c9f521f3db6942d27f8ccdf06b180160490eef8bdbc AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.4.1@sha256:bbb299c7e58a2c61c683dc3439da3e2698e005ca88ba6fd249629311d70250ab
COPY --from=ubi-micro-build /mnt/rootfs /
