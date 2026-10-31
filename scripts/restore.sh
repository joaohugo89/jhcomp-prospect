#!/usr/bin/env bash
# Restaura um backup no banco em uso (D27). APAGA os dados atuais.
#
#   scripts/restore.sh backups/jhcomp-prospect-20261106T031500Z.dump --yes
#
# Antes de apagar: confere o SHA-256, confere que o dump e legivel e faz um
# backup de seguranca do estado atual. Se esse backup falhar (banco
# corrompido, por exemplo), o script para; --no-safety-backup pula o passo
# conscientemente. Prefira validar o dump antes com scripts/verify-backup.sh.
set -euo pipefail

cd "$(dirname "$0")/.."

dump="${1:?uso: scripts/restore.sh <arquivo.dump> --yes [--no-safety-backup]}"
shift
confirmed=false
safety_backup=true
for arg in "$@"; do
  case "$arg" in
    --yes) confirmed=true ;;
    --no-safety-backup) safety_backup=false ;;
    *) echo "Opcao desconhecida: $arg" >&2; exit 2 ;;
  esac
done

[[ -f "$dump" ]] || { echo "Arquivo nao encontrado: $dump" >&2; exit 2; }
if [[ "$confirmed" != true ]]; then
  echo "Restaurar APAGA todos os dados atuais do banco e os substitui pelo backup." >&2
  echo "Repita o comando com --yes para confirmar." >&2
  exit 2
fi

if [[ -f "$dump.sha256" ]]; then
  (cd "$(dirname "$dump")" && sha256sum --check --quiet "$(basename "$dump").sha256")
fi
docker compose exec -T postgres pg_restore --list < "$dump" > /dev/null

if [[ "$safety_backup" == true ]]; then
  echo "Backup de seguranca do estado atual..."
  scripts/backup.sh
fi

echo "Parando a API para que nada grave durante a restauracao..."
docker compose stop backend

docker compose exec -T postgres sh -c \
  'dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose exec -T postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < "$dump"

echo "Subindo a API..."
docker compose start backend
for _ in $(seq 1 60); do
  if docker compose ps backend --format '{{.Health}}' | grep -q '^healthy$'; then
    echo "Restauracao concluida; API saudavel."
    exit 0
  fi
  sleep 3
done
echo "A API nao ficou saudavel em 3 minutos. Veja: docker compose logs backend" >&2
exit 1
