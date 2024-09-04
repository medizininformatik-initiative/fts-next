# Monitoring-Util

This module contains an example configuration to utilize Prometheus to collect metrics from CDA, RDA, and TCA and
visualize them with Grafana.

When using `compose.yaml`make sure to set the NETWORK_NAME environment variable to the same network
of CDA, RDA, and TCA, e.g. `test_agents`, if you are running tests in `.github/test`.
