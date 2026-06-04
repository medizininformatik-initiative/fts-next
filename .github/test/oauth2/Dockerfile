FROM registry.access.redhat.com/ubi9@sha256:157ac93fde9596b9e04908fa9a22746350f53452718af9562289239f68a505c6 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:26.6.2@sha256:f9ba7b2af90db8dc749a57ca9aedca51e840cb9224441ab546a968da941da900
COPY --from=ubi-micro-build /mnt/rootfs /
