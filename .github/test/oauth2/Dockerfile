FROM registry.access.redhat.com/ubi9@sha256:53d6c19d664f4f418ce5c823d3a33dbb562a2550ea249cf07ef10aa063ace38f AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.1.0@sha256:ac430f92e286fdcbef10bc8ea6e2b2472eae5838dc264221be338f6733453e70
COPY --from=ubi-micro-build /mnt/rootfs /
