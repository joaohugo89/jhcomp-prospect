#!/usr/bin/env bash
# Prova que um backup restaura (D27): sobe um PostgreSQL descartavel, restaura
# o dump nele e mostra a versao do schema e a contagem das tabelas principais.
#
#   scripts/verify-backup.sh backups/jhcomp-prospect-20261106T031500Z.dump
#   scripts/verify-backup.sh <dump> --compare   # compara com o banco em uso
#
# Nao toca no banco em uso: --compare so le. Com --compare, sai com erro se
# alguma contagem divergir, o que so faz sentido logo apos o backup, antes de
# novas gravacoes.
set -euo pipefail

cd "$(dirname "$0")/.."

dump="${1:?uso: scripts/verify-backup.sh <arquivo.dump> [--compare]}"
compare="${2:-}"
[[ -f "$dump" ]] || { echo "Arquivo nao encontrado: $dump" >&2; exit 2; }

if [[ -f "$dump.sha256" ]]; then
  (cd "$(dirname "$dump")" && sha256sum --check --quiet "$(basename "$dump").sha256")
  echo "SHA-256 confere."
fi

QUERY="
select 'schema_version', (select version from flyway_schema_history where success order by installed_rank desc limit 1)
union all select 'organization', count(*)::text from organization
union all select 'app_user', count(*)::text from app_user
union all select 'company', count(*)::text from company
union all select 'lead', count(*)::text from lead
union all select 'interaction', count(*)::text from interaction
union all select 'outreach_message', count(*)::text from outreach_message
union all select 'suppression_entry', count(*)::text from suppression_entry
union all select 'audit_event', count(*)::text from audit_event;"

container="jhcomp-restore-check-$$"
docker run -d --rm --name "$container" \
  -e POSTGRES_USER=verify -e POSTGRES_PASSWORD=verify-only -e POSTGRES_DB=verify \
  postgres:18-alpine > /dev/null
trap 'docker rm -f "$container" > /dev/null 2>&1 || true' EXIT

# Por TCP, e nao pelo socket: durante a inicializacao a imagem sobe um servidor
# temporario so no socket, que e derrubado logo depois.
for _ in $(seq 1 60); do
  docker exec "$container" pg_isready -h 127.0.0.1 -U verify -d verify > /dev/null 2>&1 && break
  sleep 1
done

docker exec -i "$container" pg_restore -U verify -d verify --no-owner --exit-on-error < "$dump"
restored="$(echo "$QUERY" | docker exec -i "$container" psql -U verify -d verify -At -F ' ')"

echo "Restaurado em banco descartavel:"
echo "$restored" | sed 's/^/  /'

if [[ "$compare" == "--compare" ]]; then
  # -T: sem TTY, que acrescentaria \r ao fim das linhas e quebraria a comparacao.
  live="$(echo "$QUERY" | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F " "')"
  if [[ "$restored" == "$live" ]]; then
    echo "Contagens iguais as do banco em uso."
  else
    echo "Divergencia com o banco em uso:" >&2
    diff <(echo "$restored") <(echo "$live") >&2 || true
    exit 1
  fi
fi

echo "Restauracao verificada."
