#!/bin/bash -e

VERSION="${ACTIONLINT_VERSION#v}"

curl -sLO "https://github.com/rhysd/actionlint/releases/download/v${VERSION}/actionlint_${VERSION}_linux_amd64.tar.gz"
tar xzf "actionlint_${VERSION}_linux_amd64.tar.gz"
rm "actionlint_${VERSION}_linux_amd64.tar.gz"
sudo mv ./actionlint /usr/local/bin/actionlint
