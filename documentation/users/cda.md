# FTS Clinical Domain Agent

## Using Docker (Recommended)

*For prerequisites, please see the docker [section](README.md#running-with-docker-recommended) of
the common usage
instructions*

* Download the latest release [artifacts][cda/docker]
* Unpack the file contents in a directory of your choice
* Configure the agent by customizing provided `application.yaml` (
  see [Configuration](#configuration))
* Run `docker compose up` from that directory
* See [usage section](#usage) for further instructions after starting the agent

## Using Java

*For prerequisites, please see the java [section](README.md#running-with-java) of the common usage
instructions*

* Download the latest release [artifacts][cda/java]
* Unpack the file contents in a directory of your choice
* Configure the agent by customizing provided `application.yaml` (
  see [Configuration](#configuration))
* Run `java -jar clinical-domain-agent-*.jar` from that directory
* See [usage section](#usage) for further instructions after starting the agent

## Configuration

The provided `application.yaml` contains hints on what can be configured. Further configuration
instructions will be
added to this document in a later release.

### Notes on the x509 authentication for the Rest API

[Here is a  very helpful internet page on setting this up in a Spring boot application] (https://www.baeldung.com/x-509-authentication-in-spring-security)

What you need to do boils down to this:

Generate a csr for your server or client, then your certificate authority can use this to generate a
signed certificate.

Then you should import your certificate and private key into a .p12 file and then a .jks keystore
file.

You should also create a truststore.jks file which contains the root certificate (the certificate
authority)

You can then enter the correct information into your application.yaml. Check the current
configuration to see how you
can set it up properly, but here is an explanation below:

```
x509-auth:
	keystore: #path to keystore.jks file
	keystore-password: #password to unlock the keystore file
	trust-store: #path to truststore.jks file
	trust-store-password: #password to unlock truststore file
```

No other configurations should be necessary in terms of making edits to the code, and you can find
all the bash commands
to do the above-mentioned steps in the link above.

## Usage

### Starting a process

A transfer process is started by sending a request to the Camunda Workflow Engine http/rest
interface of the CD agent.
Currently, no consent information is evaluated so the list of pids that need to be transferred must
be specified in the request as a comma-separated list. This only works when CohortSelectionViaPost is
configured.

Note that the following example is based on the default configuration, the protocol (https when ssl
is used), authentication, hostname and port must be changed on a productive system.

```bash
curl https://localhost:9090/engine-rest/engine/default/process-definition/key/smith-cda-process/start \
  -u user:pwd \
  -X POST \
  --data-raw '{
                "variables": {
                  "cohort" : {
                    "value": "1234,5678",
                    "type": "String"
                  }
                }
              }'
```

This will trigger the cda and call the cda-pid process for every pid in the pid-list.

## Monitoring

Prometheus metrics can be found at `http://localhost:9090/actuator/prometheus`

[cda/java]: https://git.smith.care/api/v4/projects/135/packages/generic/clinical-domain-agent/4.2.0/clinical-domain-agent-java.zip

[cda/docker]: https://git.smith.care/api/v4/projects/135/packages/generic/clinical-domain-agent/4.2.0/clinical-domain-agent-docker.zip
