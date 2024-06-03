#!/bin/bash
set -o errexit

mvn clean install --projects .
mvn clean install --projects util
mvn clean install --projects api
mvn clean package --projects clinical-domain-agent
mvn clean package --projects research-domain-agent
mvn clean package --projects trust-center-agent
