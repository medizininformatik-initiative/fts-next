FROM ghcr.io/knoppiks/mosaic-gpas:2024.3.0

# Download MariaDB JDBC driver
ADD --chmod=644 https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.4.1/mariadb-java-client-3.4.1.jar /tmp/

# Install MariaDB module and configure datasource via CLI
COPY --chmod=644 wildfly-cli/mariadb-setup.cli /entrypoint-wildfly-cli/
