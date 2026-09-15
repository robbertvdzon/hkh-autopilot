#!/usr/bin/env bash
set -euo pipefail

deploy_root="${DEPLOY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
mode="${1:-update}"

update_or_check() {
  local secret_manifest="$1" overlay="$2" expected actual
  expected="$(sha256sum "${secret_manifest}" | cut -d' ' -f1)"
  actual="$(awk '/hkh.vdzonsoftware.nl~1runtime-secret-checksum/{getline; gsub(/.*value: *"|".*/, ""); print; exit}' "${overlay}")"

  if [[ "${mode}" == "--check" ]]; then
    [[ "${actual}" == "${expected}" ]] || {
      echo "Runtime-secretchecksum is verouderd in ${overlay#"${deploy_root}/"}." >&2
      exit 1
    }
    return
  fi

  sed -i "/hkh.vdzonsoftware.nl~1runtime-secret-checksum/{n;s/value: \".*\"/value: \"${expected}\"/}" "${overlay}"
}

[[ "${mode}" == "update" || "${mode}" == "--check" ]] || {
  echo "Gebruik: $0 [--check]" >&2
  exit 2
}

update_or_check \
  "${deploy_root}/base/sealed-secret-runtime.yaml" \
  "${deploy_root}/overlays/openshift/kustomization.yaml"
update_or_check \
  "${deploy_root}/overlays/acceptance/acceptance-secret.yaml" \
  "${deploy_root}/overlays/acceptance/kustomization.yaml"

echo "Runtime-secretchecksums bijgewerkt of gecontroleerd."
