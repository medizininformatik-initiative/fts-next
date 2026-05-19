FROM registry.access.redhat.com/ubi9@sha256:8ca59004c1c505bdabadd5202bd3363986f5bf873fcfb36f60561d7362fe52a7 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.6.2@sha256:e712055eab50036a06dda2921ee5158e4fdf8d362402ae3886cc0eb97012cffb
COPY --from=ubi-micro-build /mnt/rootfs /
