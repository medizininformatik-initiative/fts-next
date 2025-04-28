#!/usr/bin/env bash

project="github.com/medizininformatik-initiative/fts-next"
image="$(grep ghcr.io/medizininformatik-initiative/fts/ <compose.yaml | cut -d':' -f2- | xargs)"
tag="${1:-<COMMIT_TAG>}"
verified=false

if slsa-verifier --help >/dev/null; then
  echo "Verifying image was built from ${project} using tag ${tag}"
  slsa-verifier verify-image "${image}" \
    --source-uri "${project}" \
    --source-tag "${tag}"
  verified=true
fi

if cosign --help >/dev/null; then
  echo "Verifying image was built from ${project} using tag ${tag}"
  cosign verify --output-file /dev/null "${image}" \
    --certificate-identity-regexp "https://${project}/.*" \
    --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
    --certificate-github-workflow-ref="refs/tags/${tag}"
  verified=true
fi

if [ $verified != true ]; then
  1>&2 echo "Verification failed, neither slsa-verifier nor cosign installed."
  exit 1
fi
