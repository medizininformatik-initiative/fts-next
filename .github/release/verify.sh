#!/usr/bin/env bash
set -euo pipefail

project="github.com/medizininformatik-initiative/fts-next"
image="$(grep ghcr.io/medizininformatik-initiative/fts/ <compose.yaml | cut -d':' -f2- | xargs)"
tag="${1:-<COMMIT_TAG>}"

echo -e "# Verifying image provenance, assert image was built \n- from ${project} \n- for tag ${tag}"

if slsa-verifier --help &>/dev/null; then
  echo -n "* Using slsa-verifier "
  if slsa-verifier verify-image "${image}" \
    --source-uri "${project}" \
    --source-tag "${tag}" &>/dev/null; then
    echo "✔ "
  else
    echo "✘ Verification failed"
    exit 1
  fi
else
  1>&2 echo "Verification failed. slsa-verifier is not installed."
  1>&2 echo "  Please see https://github.com/slsa-framework/slsa-verifier#installation"
  exit 1
fi

if cosign --help &>/dev/null; then
  echo -n "* Using cosign "
  if cosign verify --output-file /dev/null "${image}" \
    --certificate-identity-regexp "https://${project}/.*" \
    --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
    --certificate-github-workflow-ref="refs/tags/${tag}" &>/dev/null; then
    echo "✔ "
  else
    echo "✘ Verification failed"
    exit 1
  fi
else
  1>&2 echo "Verification failed. cosign is not installed."
  1>&2 echo "  Please see https://docs.sigstore.dev/cosign/system_config/installation/"
  exit 1
fi
