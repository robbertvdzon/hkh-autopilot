#!/usr/bin/env bash
set -euo pipefail

deploy_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "${temporary_dir}"' EXIT

"${deploy_root}/update-runtime-secret-checksums.sh" --check >/dev/null

rendered_acceptance="${temporary_dir}/acceptance.yaml"
kubectl kustomize "${deploy_root}/overlays/acceptance" > "${rendered_acceptance}"
grep -Fq 'hkh.vdzonsoftware.nl/runtime-secret-checksum:' "${rendered_acceptance}"
grep -Fq 'name: hkh-runtime-acceptance' "${rendered_acceptance}"

# Simuleer in een geïsoleerde kopie dat uitsluitend versleuteld secretmateriaal verandert. De
# updater moet dan een andere Pod-templatechecksum schrijven; secretinhoud wordt niet uitgelezen.
fixture_root="${temporary_dir}/deploy"
mkdir -p "${fixture_root}/base" "${fixture_root}/overlays/openshift" "${fixture_root}/overlays/acceptance"
cp "${deploy_root}/base/sealed-secret-runtime.yaml" "${fixture_root}/base/sealed-secret-runtime.yaml"
cp "${deploy_root}/overlays/openshift/kustomization.yaml" "${fixture_root}/overlays/openshift/kustomization.yaml"
cp "${deploy_root}/overlays/acceptance/acceptance-secret.yaml" "${fixture_root}/overlays/acceptance/acceptance-secret.yaml"
cp "${deploy_root}/overlays/acceptance/kustomization.yaml" "${fixture_root}/overlays/acceptance/kustomization.yaml"

before="$(awk '/hkh.vdzonsoftware.nl~1runtime-secret-checksum/{getline; gsub(/.*value: *"|".*/, ""); print; exit}' "${fixture_root}/overlays/acceptance/kustomization.yaml")"
printf '\n# encrypted-material-change-simulation\n' >> "${fixture_root}/overlays/acceptance/acceptance-secret.yaml"
DEPLOY_ROOT="${fixture_root}" "${deploy_root}/update-runtime-secret-checksums.sh" >/dev/null
after="$(awk '/hkh.vdzonsoftware.nl~1runtime-secret-checksum/{getline; gsub(/.*value: *"|".*/, ""); print; exit}' "${fixture_root}/overlays/acceptance/kustomization.yaml")"
expected="$(sha256sum "${fixture_root}/overlays/acceptance/acceptance-secret.yaml" | cut -d' ' -f1)"

[[ "${before}" != "${after}" ]]
[[ "${after}" == "${expected}" ]]
echo "Runtime-secretwijziging vernieuwt de backend Pod-templatechecksum."
