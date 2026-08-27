#!/usr/bin/env bash
set -euo pipefail

deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "${deploy_dir}/.." && pwd)"
runtime_source="${HKH_AGENT_RUNTIME_SECRET_SOURCE:-${root_dir}/../agent-runtime/secrets.env}"

[[ -f "${runtime_source}" && ! -L "${runtime_source}" ]] || {
  echo "Veilige Agent Runtime-secretbron ontbreekt." >&2
  exit 1
}

value_for() {
  local file="$1" key="$2"
  awk -v key="${key}" 'index($0,key "=")==1 {print substr($0,length(key)+2)}' "${file}" | tail -1
}

token="$(value_for "${runtime_source}" AR_HKH_AUTOPILOT_TOKEN)"
[[ ${#token} -ge 24 ]] || {
  echo "AR_HKH_AUTOPILOT_TOKEN ontbreekt of is te kort." >&2
  exit 1
}

prepare_target() {
  local target="$1" template="$2"
  if [[ ! -f "${target}" ]]; then
    cp "${template}" "${target}"
  fi
  [[ -f "${target}" && ! -L "${target}" ]] || {
    echo "Onveilige secretfile geweigerd: ${target}" >&2
    exit 1
  }
  chmod 600 "${target}"
}

upsert() {
  local target="$1" key="$2" value="$3" temporary
  temporary="$(mktemp)"
  chmod 600 "${temporary}"
  awk -v key="${key}" 'index($0,key "=")!=1 {print}' "${target}" > "${temporary}"
  printf '%s=%s\n' "${key}" "${value}" >> "${temporary}"
  mv "${temporary}" "${target}"
  chmod 600 "${target}"
}

configure() {
  local target="$1" url="$2" provider="$3"
  upsert "${target}" HKH_AGENT_RUNTIME_URL "${url}"
  upsert "${target}" HKH_AGENT_RUNTIME_TOKEN "${token}"
  upsert "${target}" HKH_AGENT_RUNTIME_PROJECT_PREFIX HKH_AUTOPILOT
  upsert "${target}" HKH_AGENT_RUNTIME_PROVIDER "${provider}"
  upsert "${target}" HKH_AGENT_RUNTIME_MODEL gpt-5.6-sol
  upsert "${target}" HKH_AGENT_RUNTIME_EXECUTION_TIMEOUT_SECONDS 3600
}

prepare_target "${root_dir}/secrets.env" "${root_dir}/secrets.env.example"
prepare_target "${deploy_dir}/secrets-cluster.env" "${deploy_dir}/secrets-cluster.env.example"
prepare_target "${deploy_dir}/secrets-acceptance.env" "${deploy_dir}/secrets-acceptance.env.example"

configure "${root_dir}/secrets.env" https://agent-runtime.vdzonsoftware.nl CODEX
configure "${deploy_dir}/secrets-cluster.env" https://agent-runtime.vdzonsoftware.nl CODEX
configure "${deploy_dir}/secrets-acceptance.env" https://agent-runtime-acceptance.vdzonsoftware.nl MOCKED

echo "Agent Runtime-configuratie veilig bijgewerkt zonder secretwaarden te tonen." >&2
