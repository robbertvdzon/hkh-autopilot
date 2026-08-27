#!/usr/bin/env bash
set -euo pipefail

deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
shared_cert="${deploy_dir}/../../robberts-infrastructure/manifests/cluster-bootstrap/cluster-cert.pem"

command -v kubeseal >/dev/null 2>&1 || {
  echo "kubeseal ontbreekt; installeer het bijvoorbeeld met: brew install kubeseal" >&2
  exit 1
}

temporary_cert=""
trap 'rm -f ${temporary_cert:+"${temporary_cert}"}' EXIT

if [[ -f "${shared_cert}" ]]; then
  cert="${shared_cert}"
else
  temporary_cert="$(mktemp)"
  kubeseal --fetch-cert > "${temporary_cert}"
  cert="${temporary_cert}"
fi

seal() {
  local source_file="$1" namespace="$2" secret_name="$3" output_file="$4" temporary_secret
  [[ -f "${source_file}" && ! -L "${source_file}" ]] || {
    echo "Veilige secretbron ontbreekt: ${source_file}" >&2
    exit 1
  }
  temporary_secret="$(mktemp)"
  chmod 600 "${temporary_secret}"
  kubectl create secret generic "${secret_name}" \
    --namespace "${namespace}" \
    --from-env-file "${source_file}" \
    --dry-run=client \
    --output yaml > "${temporary_secret}"
  kubeseal --cert "${cert}" --format yaml < "${temporary_secret}" > "${output_file}"
  rm -f "${temporary_secret}"
  echo "Versleuteld secret geschreven naar ${output_file}."
}

seal "${deploy_dir}/secrets-cluster.env" hkh-autopilot hkh-runtime \
  "${deploy_dir}/base/sealed-secret-runtime.yaml"
seal "${deploy_dir}/secrets-acceptance.env" hkh-autopilot-acceptance hkh-runtime-acceptance \
  "${deploy_dir}/overlays/acceptance/acceptance-secret.yaml"
