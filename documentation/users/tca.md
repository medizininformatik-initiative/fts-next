# Trust Center Agent

## Using Docker (Recommended)

*For prerequisites, please see the docker [section](README.md#running-with-docker-recommended) of
the common usage
instructions*

* Download the latest release [artifacts][tca/docker]
* Unpack the file contents in a directory of your choice
* Configure the agent by customizing provided `application.yaml` (
  see [Configuration](#configuration))
* Run `docker compose up` from that directory
* See [usage section](#usage) for further instructions after starting the agent

## Using Java

*For prerequisites, please see the java [section](README.md#running-with-java) of the common usage
instructions*

* Download the latest release [artifacts][tca/java]
* Unpack the file contents in a directory of your choice
* Configure the agent by customizing provided `application.yaml` (
  see [Configuration](#configuration))
* Run `java -jar trust-center-agent-*.jar` from that directory
* See [usage section](#usage) for further instructions after starting the agent

## Configuration

The provided `application.yaml` contains hints on what can be configured.

### Notes on the x509 authentication for the Rest API

[Here is a  very helpful internet page on setting this up in a Spring boot application] (https://www.baeldung.com/x-509-authentication-in-spring-security)

What you need to do boils down to this:

Generate a csr for your server or client, then your certificate authority can use this to generate a
signed certificate.

Then you should import your certificate and private key into a .p12 file and then a .jks keystore
file.

You should also create a truststore.jks file which contains the root certificate (the certificate
authority)

You can then enter the correct information into your application.yaml
The following data must go under server.ssl, check the current configuration to see how you can set
it up properly, but
here is an explanation below:

```
server:
  port: 8443
  ssl:
	key-store: #path to keystore.jks file
	key-store-password: #password to unlock the keystore
	key-alias: #the value you set when generating the keystore (the -alias option) if unsure run this command on your keystore and check the output: keytool -v -list -keystore keystore.jks | grep -i alias
	trust-store: #path to truststore.jks file
	trust-store-password:# password to unlock truststore
	client-auth: #'need' in order to enforce x509 cert auth, 'want' to keep ssl using this cert, but you will need a different authentication mechanism
	enable: true
```

No other configurations should be necessary in terms of making edits to the code, and you can find
all the bash commands
to do the above-mentioned steps in the link above.

## Usage

The TCA API is described [here](tca/api.md).

## Monitoring

Prometheus metrics can be found at `http://localhost:9090/actuator/prometheus`

[tca/java]: https://git.smith.care/api/v4/projects/135/packages/generic/trust-center-agent/4.2.0/trust-center-agent-java.zip

[tca/docker]: https://git.smith.care/api/v4/projects/135/packages/generic/trust-center-agent/4.2.0/trust-center-agent-docker.zip
