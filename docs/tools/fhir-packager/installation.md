# Installation

The FHIR Packager can be installed and deployed in multiple ways depending on your environment and requirements.

## Prerequisites

### System Requirements

- **Java**: Version 21 or higher
- **Memory**: Minimum 1GB RAM, 2GB+ recommended for large bundles
- **Network**: Access to FHIR Pseudonymizer REST service

### Development Requirements

- **Maven**: Version 3.6 or higher (for building from source)
- **Git**: For cloning the repository
- **Docker**: Optional, for containerized deployment

## Installation Methods

### Method 1: Build from Source

This is the recommended method for development and customization.

#### 1. Clone the Repository

```bash
# Clone the main FTSnext repository
git clone https://github.com/medizininformatik-initiative/fts-next.git
cd fts-next/fhir-packager
```

#### 2. Build the Executable JAR

```bash
# Build the project
mvn clean package

# The executable JAR will be created at:
# target/fhir-packager-{version}.jar
```

#### 3. Verify the Installation

```bash
# Check version
java -jar target/fhir-packager-*.jar --version

# Show help
java -jar target/fhir-packager-*.jar --help
```

### Method 2: Download Release Artifacts

::: warning Coming Soon
Pre-built release artifacts will be available in future versions through GitHub Releases.
:::

```bash
# Download from GitHub releases (when available)
wget https://github.com/medizininformatik-initiative/fts-next/releases/download/v{version}/fhir-packager-{version}.jar

# Make executable (optional, for convenience)
chmod +x fhir-packager-{version}.jar
```

### Method 3: Docker Container

::: info Future Enhancement
Docker containerization is planned for a future release.
:::

```bash
# Build Docker image
docker build -t fhir-packager .

# Run with Docker
docker run -i fhir-packager:latest < input-bundle.json > output-bundle.json
```

## Deployment Options

### Standalone Deployment

The simplest deployment for single-machine usage:

```bash
# Create installation directory
sudo mkdir -p /opt/fhir-packager
sudo cp target/fhir-packager-*.jar /opt/fhir-packager/

# Create wrapper script
sudo tee /usr/local/bin/fhir-packager << 'EOF'
#!/bin/bash
java -jar /opt/fhir-packager/fhir-packager-*.jar "$@"
EOF

sudo chmod +x /usr/local/bin/fhir-packager

# Test installation
echo '{}' | fhir-packager --help
```

### System Service Deployment

For continuous processing scenarios:

```bash
# Create service user
sudo useradd -r -s /bin/false fhir-packager

# Create systemd service
sudo tee /etc/systemd/system/fhir-packager@.service << 'EOF'
[Unit]
Description=FHIR Packager Processor for %i
After=network.target

[Service]
Type=simple
User=fhir-packager
ExecStart=/usr/local/bin/fhir-packager-processor %i
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
```

### Container Orchestration

For Kubernetes or Docker Swarm deployments:

```yaml
# kubernetes-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fhir-packager-processor
spec:
  replicas: 3
  selector:
    matchLabels:
      app: fhir-packager
  template:
    metadata:
      labels:
        app: fhir-packager
    spec:
      containers:
      - name: fhir-packager
        image: fhir-packager:latest
        env:
        - name: PSEUDONYMIZER_URL
          value: "https://pseudonymizer-service:8080"
        resources:
          requests:
            memory: "512Mi"
            cpu: "100m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
```

## Configuration Setup

### Basic Configuration

Create a configuration file for your environment:

```bash
# Create config directory
mkdir -p ~/.config/fhir-packager

# Create basic configuration
cat > ~/.config/fhir-packager/application.yaml << 'EOF'
pseudonymizer:
  url: http://localhost:8080
  connect-timeout: 30s
  read-timeout: 60s
  health-check-enabled: true
  retry:
    max-attempts: 3
    min-backoff: 1s
    max-backoff: 10s

logging:
  level:
    care.smith.fts.packager: INFO
EOF
```

### Environment Variables

Set up environment variables for your deployment:

```bash
# Add to ~/.bashrc or system profile
export PSEUDONYMIZER_URL=https://pseudonymizer.example.com
export PSEUDONYMIZER_CONNECT_TIMEOUT=45
export PSEUDONYMIZER_READ_TIMEOUT=120
export PSEUDONYMIZER_RETRY_MAX_ATTEMPTS=5
export PSEUDONYMIZER_HEALTH_CHECK_ENABLED=true
```

### Production Configuration

For production deployments, consider:

```yaml
# production.yaml
pseudonymizer:
  url: ${PSEUDONYMIZER_URL}
  connect-timeout: 45s
  read-timeout: 120s
  health-check-enabled: true
  retry:
    max-attempts: 5
    min-backoff: 2s
    max-backoff: 30s
    backoff-multiplier: 2.0
    jitter: 0.1

logging:
  level:
    root: WARN
    care.smith.fts.packager: INFO
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/fhir-packager/application.log
    max-size: 100MB
    max-history: 30
```

## Verification

### Basic Functionality Test

```bash
# Test with minimal valid FHIR Bundle
echo '{
  "resourceType": "Bundle",
  "id": "test-bundle",
  "type": "collection",
  "entry": []
}' | java -jar target/fhir-packager-*.jar --pseudonymizer-url http://localhost:8080
```

### Service Connectivity Test

```bash
# Test service availability
curl -f http://localhost:8080/health || echo "Service not available"

# Test with health check enabled
echo '{"resourceType": "Bundle", "type": "collection", "entry": []}' | \
  java -jar target/fhir-packager-*.jar \
  --pseudonymizer-url http://localhost:8080 \
  --verbose 2>&1 | grep -i "health"
```

### Performance Test

```bash
# Generate test bundle
cat > large-test-bundle.json << 'EOF'
{
  "resourceType": "Bundle",
  "id": "performance-test",
  "type": "collection",
  "entry": [
    {"resource": {"resourceType": "Patient", "id": "p1"}},
    {"resource": {"resourceType": "Observation", "id": "o1"}},
    {"resource": {"resourceType": "DiagnosticReport", "id": "d1"}}
  ]
}
EOF

# Time the processing
time cat large-test-bundle.json | java -jar target/fhir-packager-*.jar > /dev/null
```

## Troubleshooting Installation

### Common Issues

#### Java Version Problems

```bash
# Check Java version
java -version

# If wrong version, update JAVA_HOME
export JAVA_HOME=/path/to/java-21
```

#### Maven Build Failures

```bash
# Clean build
mvn clean

# Build with debug info
mvn clean package -X

# Skip tests if needed
mvn clean package -DskipTests
```

#### Memory Issues

```bash
# Increase JVM memory for build
export MAVEN_OPTS="-Xmx2g"
mvn clean package

# Or specify directly
mvn clean package -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
```

#### Permission Issues

```bash
# Fix file permissions
chmod +x target/fhir-packager-*.jar

# Fix directory permissions
sudo chown -R $USER:$USER ~/.m2/repository
```

### Validation Commands

```bash
# Verify JAR integrity
java -jar target/fhir-packager-*.jar --version

# Check dependencies
mvn dependency:tree

# Validate configuration
java -jar target/fhir-packager-*.jar --help
```

## Next Steps

1. [Configure](./configuration) the tool for your environment
2. Review [usage examples](./usage) for integration patterns
3. Set up [troubleshooting](./troubleshooting) procedures
4. Consider performance tuning for your workload

## Upgrade Procedure

When upgrading to a new version:

1. **Backup current configuration**:
   ```bash
   cp ~/.config/fhir-packager/application.yaml application.yaml.backup
   ```

2. **Build new version**:
   ```bash
   git pull
   mvn clean package
   ```

3. **Test with backup data**:
   ```bash
   cat test-bundle.json | java -jar target/fhir-packager-*.jar > test-output.json
   ```

4. **Deploy to production**:
   ```bash
   sudo cp target/fhir-packager-*.jar /opt/fhir-packager/
   sudo systemctl restart fhir-packager-processor
   ```