FROM registry.access.redhat.com/ubi9@sha256:ea57285741f007e83f2ee20423c20b0cbcce0b59cc3da027c671692cc7efe4dd AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.2.0@sha256:87758ff2293c78c942c7a1f0df2bc13e0f943fcf0c0d027c12fdfac54a35d93b
COPY --from=ubi-micro-build /mnt/rootfs /
