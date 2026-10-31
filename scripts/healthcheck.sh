#!/usr/bin/env bash
# Verificacao periodica do servidor (D27). Sai com erro e, se ALERT_WEBHOOK_URL
# estiver no .env, envia um POST com o motivo. Para cron, a cada 5 minutos:
#
#   */5 * * * *  /opt/jhcomp-prospect/scripts/healthcheck.sh > /dev/null
#
# Confere: todos os containers saudaveis, /api/v1/health pelo dominio publico
# (TLS, Caddy, nginx, API e banco no mesmo pedido), idade do ultimo backup e
# espaco em disco. Complementa, nao substitui, um monitor externo: se o
# servidor cair inteiro, este script cai junto.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

env_value() {
  grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- || true
}

APP_DOMAIN="${APP_DOMAIN:-$(env_value APP_DOMAIN)}"
BACKUP_DIR="${BACKUP_DIR:-$(env_value BACKUP_DIR)}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-$(env_value ALERT_WEBHOOK_URL)}"
MAX_BACKUP_AGE_HOURS="${MAX_BACKUP_AGE_HOURS:-26}"
MAX_DISK_PERCENT="${MAX_DISK_PERCENT:-85}"

problems=()

for service in postgres backend frontend caddy; do
  health="$(docker compose ps --all "$service" --format '{{.State}} {{.Health}}' 2>/dev/null)"
  case "$health" in
    "running healthy" | "running ") ;;
    *) problems+=("container $service: ${health:-ausente}") ;;
  esac
done

if [[ -n "$APP_DOMAIN" ]]; then
  status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$APP_DOMAIN/api/v1/health")"
  [[ "$status" == "200" ]] || problems+=("health publico respondeu $status")
fi

latest="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'jhcomp-prospect-*.dump' -mmin "-$((MAX_BACKUP_AGE_HOURS * 60))" 2>/dev/null | head -1)"
[[ -n "$latest" ]] || problems+=("nenhum backup nas ultimas ${MAX_BACKUP_AGE_HOURS}h em $BACKUP_DIR")

used="$(df --output=pcent / 2>/dev/null | tail -1 | tr -dc '0-9')"
if [[ -n "$used" && "$used" -ge "$MAX_DISK_PERCENT" ]]; then
  problems+=("disco em ${used}%")
fi

if [[ ${#problems[@]} -eq 0 ]]; then
  echo "ok"
  exit 0
fi

message="JH.COMP Prospect ($(hostname)): $(IFS='; '; echo "${problems[*]}")"
echo "$message" >&2
if [[ -n "$ALERT_WEBHOOK_URL" ]]; then
  # Sem dado pessoal nem segredo: so o nome do problema.
  curl -s --max-time 10 -X POST -H 'Content-Type: application/json' \
    -d "{\"text\": \"${message//\"/\'}\"}" "$ALERT_WEBHOOK_URL" > /dev/null || true
fi
exit 1
